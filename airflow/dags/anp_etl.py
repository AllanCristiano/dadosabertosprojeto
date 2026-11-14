"""
DAG de ETL da série histórica de preços de combustíveis (ANP, dados abertos).

Fluxo:  planejar -> extrair+validar+carregar staging -> transformar (dims/fato) -> refresh -> checagem final

Decisões:
* Incremental: só processa semestres ausentes em dw.etl_controle + o semestre corrente
  (a ANP republica o arquivo corrente com novas coletas).
* Idempotente: staging é limpo por arquivo antes de recarregar e a fato usa
  INSERT ... ON CONFLICT DO UPDATE na PK natural. Rodar 2x produz o mesmo estado.
* Qualidade: linhas inválidas são descartadas e contadas; a task falha se a taxa de rejeição
  passar de MAX_REJECT_RATIO ou se faltar coluna obrigatória.
* Alerta: on_failure_callback loga e, se ALERT_WEBHOOK_URL existir, faz POST (Slack/Discord/Teams).
"""
from __future__ import annotations

import csv
import io
import logging
import os
import re
import unicodedata
import zipfile
from datetime import date, datetime, timedelta

import requests
from airflow.decorators import dag, task
from airflow.providers.postgres.hooks.postgres import PostgresHook

log = logging.getLogger(__name__)

BASE_URL = os.getenv(
    "ANP_BASE_URL",
    "https://www.gov.br/anp/pt-br/centrais-de-conteudo/dados-abertos/arquivos/shpc/dsas/ca",
)
FIRST_YEAR = int(os.getenv("ANP_FIRST_YEAR", "2025"))
MAX_REJECT_RATIO = float(os.getenv("ANP_MAX_REJECT_RATIO", "0.05"))
PRICE_MIN, PRICE_MAX = 0.5, 30.0
REQUIRED = ["estado - sigla", "municipio", "cnpj da revenda", "produto", "data da coleta", "valor de venda"]
CONN_ID = "anp_dw"
RABBITMQ_MGMT_URL = os.getenv("RABBITMQ_MGMT_URL", "")   # vazio = não publica evento
RABBITMQ_AUTH = (os.getenv("RABBITMQ_USER", "guest"), os.getenv("RABBITMQ_PASSWORD", "guest"))
EVENT_EXCHANGE, EVENT_ROUTING_KEY = "anp.eventos", "carga.concluida"


def notify_failure(context):
    ti = context["task_instance"]
    msg = f"[ANP-ETL] FALHA em {ti.dag_id}.{ti.task_id} (run {context['run_id']}): {context.get('exception')}"
    log.error(msg)
    url = os.getenv("ALERT_WEBHOOK_URL")
    if url:
        try:
            requests.post(url, json={"text": msg, "content": msg}, timeout=10)
        except requests.RequestException:
            log.exception("Não foi possível enviar o alerta")


def publicar_evento_carga() -> bool:
    """Avisa a API (via RabbitMQ) que há dados novos; ela avalia as regras de alerta.

    Best-effort: a carga já está válida, então falha de mensageria só gera aviso em log.
    """
    if not RABBITMQ_MGMT_URL:
        log.info("RABBITMQ_MGMT_URL ausente; evento não publicado")
        return False
    try:
        resp = requests.post(
            f"{RABBITMQ_MGMT_URL}/api/exchanges/%2F/{EVENT_EXCHANGE}/publish",
            json={"properties": {"content_type": "application/json"}, "routing_key": EVENT_ROUTING_KEY,
                  "payload": "{}", "payload_encoding": "string"},
            auth=RABBITMQ_AUTH, timeout=10)
        resp.raise_for_status()
        routed = resp.json().get("routed")
        log.info("Evento %s publicado (roteado=%s)", EVENT_ROUTING_KEY, routed)
        return bool(routed)
    except requests.RequestException as exc:
        log.warning("Não foi possível publicar o evento (a carga segue válida): %s", exc)
        return False


def _norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.strip().lower())
    return "".join(c for c in s if not unicodedata.combining(c))


def _num(v: str):
    v = (v or "").strip()
    if "," in v:
        v = v.replace(".", "").replace(",", ".")
    return float(v) if v else None


def _baixar(arquivo: str):
    """Baixa o semestre: a ANP publica .zip (2022+) ou .csv (anteriores). Retorna bytes do CSV ou None se 404."""
    for ext in ("zip", "csv"):
        resp = requests.get(f"{BASE_URL}/{arquivo}.{ext}", timeout=300, headers={"User-Agent": "Mozilla/5.0"})
        if resp.status_code == 404:
            continue
        resp.raise_for_status()
        if ext == "csv":
            return resp.content
        with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
            return z.read(next(n for n in z.namelist() if n.lower().endswith(".csv")))
    return None


def _semesters_until_now():
    today = date.today()
    for y in range(FIRST_YEAR, today.year + 1):
        for s in (1, 2):
            if y == today.year and s == 2 and today.month < 7:
                continue
            yield f"ca-{y}-0{s}"


@dag(
    dag_id="anp_combustiveis_etl",
    description="ETL incremental e idempotente: preços de combustíveis ANP -> PostgreSQL (dimensional)",
    schedule="0 6 * * 1",  # semanal, segunda 06:00
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args={
        "owner": "data-eng",
        "retries": 2,
        "retry_delay": timedelta(minutes=5),
        "on_failure_callback": notify_failure,
    },
    tags=["anp", "etl", "combustiveis"],
)
def anp_etl():
    @task
    def planejar(**ctx) -> list[str]:
        """Incremental: semestres ainda não carregados + semestre corrente. `params.force` reprocessa tudo."""
        hook = PostgresHook(CONN_ID)
        done = {r[0] for r in hook.get_records("SELECT arquivo FROM dw.etl_controle")}
        todos = list(_semesters_until_now())
        force = bool((ctx.get("params") or {}).get("force"))
        alvo = todos if force else [a for a in todos if a not in done] + ([todos[-1]] if todos[-1] in done else [])
        log.info("Arquivos a processar: %s", alvo)
        return alvo

    @task(max_active_tis_per_dag=2)
    def extrair_validar_carregar(arquivo: str) -> dict:
        hook = PostgresHook(CONN_ID)
        raw = _baixar(arquivo)
        if raw is None:
            log.warning("%s ainda não publicado (404) — ignorando", arquivo)
            return {"arquivo": arquivo, "skipped": True}
        try:
            text = raw.decode("utf-8-sig")
        except UnicodeDecodeError:
            text = raw.decode("latin-1")

        reader = csv.reader(io.StringIO(text), delimiter=";")
        header = [_norm(h) for h in next(reader)]
        missing = [c for c in REQUIRED if c not in header]
        if missing:
            raise ValueError(f"{arquivo}: colunas obrigatórias ausentes: {missing}")
        ix = {c: header.index(c) for c in header}
        get = lambda row, k: row[ix[k]].strip() if k in ix and ix[k] < len(row) else ""  # noqa: E731

        out, lidas, validas = io.StringIO(), 0, 0
        w = csv.writer(out)
        for row in reader:
            lidas += 1
            try:
                dt = datetime.strptime(get(row, "data da coleta"), "%d/%m/%Y").date()
                pv = _num(get(row, "valor de venda"))
                pc = _num(get(row, "valor de compra"))
                uf, cnpj = get(row, "estado - sigla").upper(), re.sub(r"\D", "", get(row, "cnpj da revenda"))
                if not (pv and PRICE_MIN <= pv <= PRICE_MAX) or len(uf) != 2 or not cnpj:
                    continue
                if pc is not None and not (PRICE_MIN <= pc <= PRICE_MAX):
                    pc = None
                if not get(row, "municipio") or not get(row, "produto"):
                    continue
            except (ValueError, TypeError):
                continue
            validas += 1
            w.writerow([
                arquivo, get(row, "regiao - sigla"), uf, get(row, "municipio").upper(), cnpj,
                get(row, "revenda"), get(row, "produto").upper(), get(row, "bandeira").upper(),
                dt.isoformat(), pv, "" if pc is None else pc,
            ])

        if lidas == 0:
            raise ValueError(f"{arquivo}: arquivo vazio")
        rejeicao = 1 - validas / lidas
        log.info("%s: %d lidas, %d válidas (rejeição %.2f%%)", arquivo, lidas, validas, rejeicao * 100)
        if rejeicao > MAX_REJECT_RATIO:
            raise ValueError(f"{arquivo}: taxa de rejeição {rejeicao:.1%} > {MAX_REJECT_RATIO:.0%}")

        conn = hook.get_conn()
        try:
            with conn, conn.cursor() as cur:
                cur.execute("DELETE FROM staging.stg_preco WHERE arquivo = %s", (arquivo,))  # idempotência
                out.seek(0)
                cur.copy_expert(
                    "COPY staging.stg_preco (arquivo, regiao, uf, municipio, cnpj, revenda, produto, bandeira,"
                    " data_coleta, preco_venda, preco_compra) FROM STDIN WITH (FORMAT csv)", out)
        finally:
            conn.close()
        return {"arquivo": arquivo, "lidas": lidas, "validas": validas}

    @task
    def transformar(resultados: list[dict]):
        """Staging -> dimensões e fato (upserts). Tudo na mesma transação por arquivo."""
        hook = PostgresHook(CONN_ID)
        for r in resultados:
            if r.get("skipped"):
                continue
            a = r["arquivo"]
            conn = hook.get_conn()
            try:
                with conn, conn.cursor() as cur:
                    cur.execute("""
                        INSERT INTO dw.dim_data (data_id, data, ano, trimestre, mes, semana)
                        SELECT DISTINCT to_char(data_coleta,'YYYYMMDD')::int, data_coleta,
                               extract(year from data_coleta), extract(quarter from data_coleta),
                               extract(month from data_coleta), extract(week from data_coleta)
                        FROM staging.stg_preco WHERE arquivo = %s
                        ON CONFLICT DO NOTHING""", (a,))
                    cur.execute("INSERT INTO dw.dim_produto (nome) SELECT DISTINCT produto FROM staging.stg_preco"
                                " WHERE arquivo=%s ON CONFLICT DO NOTHING", (a,))
                    cur.execute("INSERT INTO dw.dim_bandeira (nome) SELECT DISTINCT bandeira FROM staging.stg_preco"
                                " WHERE arquivo=%s AND bandeira <> '' ON CONFLICT DO NOTHING", (a,))
                    cur.execute("""
                        INSERT INTO dw.dim_localidade (regiao, uf, municipio)
                        SELECT DISTINCT ON (uf, municipio) regiao, uf, municipio FROM staging.stg_preco WHERE arquivo=%s
                        ON CONFLICT (uf, municipio) DO NOTHING""", (a,))
                    cur.execute("""
                        INSERT INTO dw.dim_revenda (cnpj, nome, bandeira_id)
                        SELECT DISTINCT ON (s.cnpj) s.cnpj, s.revenda, b.bandeira_id
                        FROM staging.stg_preco s LEFT JOIN dw.dim_bandeira b ON b.nome = s.bandeira
                        WHERE s.arquivo=%s ORDER BY s.cnpj, s.data_coleta DESC
                        ON CONFLICT (cnpj) DO UPDATE SET nome = EXCLUDED.nome, bandeira_id = EXCLUDED.bandeira_id""", (a,))
                    cur.execute("""
                        INSERT INTO dw.fato_preco (data_id, revenda_id, produto_id, localidade_id, preco_venda, preco_compra)
                        SELECT DISTINCT ON (d.data_id, r.revenda_id, p.produto_id)
                               d.data_id, r.revenda_id, p.produto_id, l.localidade_id, s.preco_venda, s.preco_compra
                        FROM staging.stg_preco s
                        JOIN dw.dim_data d ON d.data = s.data_coleta
                        JOIN dw.dim_revenda r ON r.cnpj = s.cnpj
                        JOIN dw.dim_produto p ON p.nome = s.produto
                        JOIN dw.dim_localidade l ON l.uf = s.uf AND l.municipio = s.municipio
                        WHERE s.arquivo = %s
                        ORDER BY d.data_id, r.revenda_id, p.produto_id
                        ON CONFLICT (data_id, revenda_id, produto_id) DO UPDATE
                          SET preco_venda = EXCLUDED.preco_venda, preco_compra = EXCLUDED.preco_compra,
                              localidade_id = EXCLUDED.localidade_id""", (a,))
                    cur.execute("""
                        INSERT INTO dw.etl_controle (arquivo, linhas_lidas, linhas_validas)
                        VALUES (%s, %s, %s)
                        ON CONFLICT (arquivo) DO UPDATE SET linhas_lidas = EXCLUDED.linhas_lidas,
                          linhas_validas = EXCLUDED.linhas_validas, carregado_em = now()""",
                                (a, r["lidas"], r["validas"]))
                    cur.execute("DELETE FROM staging.stg_preco WHERE arquivo = %s", (a,))
            finally:
                conn.close()

    @task
    def atualizar_e_checar():
        hook = PostgresHook(CONN_ID)
        hook.run("REFRESH MATERIALIZED VIEW CONCURRENTLY dw.mv_preco_mensal_uf")
        # Checagens de qualidade pós-carga
        orfas = hook.get_first("""SELECT count(*) FROM dw.fato_preco f
                                   LEFT JOIN dw.dim_revenda r USING (revenda_id) WHERE r.revenda_id IS NULL""")[0]
        total = hook.get_first("SELECT count(*) FROM dw.fato_preco")[0]
        if orfas or total == 0:
            raise ValueError(f"Qualidade: fato vazia ({total}) ou com órfãs ({orfas})")
        log.info("OK: %d linhas na fato", total)

    @task
    def publicar_carga_concluida():
        publicar_evento_carga()

    arquivos = planejar()
    carregados = extrair_validar_carregar.expand(arquivo=arquivos)
    transformar(carregados) >> atualizar_e_checar() >> publicar_carga_concluida()


anp_etl()
