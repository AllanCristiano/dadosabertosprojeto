import io
import zipfile
from datetime import date
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import anp_etl
import pytest
import requests


# ---------- _norm / _num
@pytest.mark.parametrize("bruto,esperado", [
    ("  Município ", "municipio"),
    ("ESTADO - SIGLA", "estado - sigla"),
    ("Preço de Venda", "preco de venda"),
    ("", ""),
])
def test_norm_remove_acentos_espacos_e_caixa(bruto, esperado):
    assert anp_etl._norm(bruto) == esperado


@pytest.mark.parametrize("bruto,esperado", [
    ("6,299", 6.299),
    ("1.234,50", 1234.5),
    ("6.299", 6.299),
    (" 7 ", 7.0),
    ("", None),
    (None, None),
])
def test_num_aceita_formato_brasileiro_e_vazio(bruto, esperado):
    assert anp_etl._num(bruto) == esperado


def test_num_rejeita_lixo():
    with pytest.raises(ValueError):
        anp_etl._num("abc")


# ---------- _semesters_until_now
def _datas(hoje):
    class FakeDate(date):
        @classmethod
        def today(cls):
            return hoje
    return FakeDate


def test_semestres_no_primeiro_semestre_nao_inclui_o_segundo_do_ano_corrente():
    with patch.object(anp_etl, "date", _datas(date(2026, 3, 1))), patch.object(anp_etl, "FIRST_YEAR", 2025):
        assert list(anp_etl._semesters_until_now()) == ["ca-2025-01", "ca-2025-02", "ca-2026-01"]


def test_semestres_a_partir_de_julho_inclui_o_segundo():
    with patch.object(anp_etl, "date", _datas(date(2026, 7, 1))), patch.object(anp_etl, "FIRST_YEAR", 2026):
        assert list(anp_etl._semesters_until_now()) == ["ca-2026-01", "ca-2026-02"]


# ---------- _baixar
def _resp(status=200, content=b""):
    r = MagicMock()
    r.status_code = status
    r.content = content
    r.raise_for_status.side_effect = None if status < 400 else requests.HTTPError(str(status))
    return r


def _zip(nome, dados):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("leiame.txt", "x")
        z.writestr(nome, dados)
    return buf.getvalue()


def test_baixar_zip_extrai_o_csv():
    with patch.object(anp_etl.requests, "get", return_value=_resp(content=_zip("ca-2025-01.CSV", b"a;b"))):
        assert anp_etl._baixar("ca-2025-01") == b"a;b"


def test_baixar_cai_para_csv_quando_zip_nao_existe():
    respostas = [_resp(404), _resp(content=b"x;y")]
    with patch.object(anp_etl.requests, "get", side_effect=respostas) as get:
        assert anp_etl._baixar("ca-2021-01") == b"x;y"
    assert get.call_args_list[0].args[0].endswith("ca-2021-01.zip")
    assert get.call_args_list[1].args[0].endswith("ca-2021-01.csv")


def test_baixar_devolve_none_quando_nenhum_formato_existe():
    with patch.object(anp_etl.requests, "get", return_value=_resp(404)):
        assert anp_etl._baixar("ca-2099-01") is None


def test_baixar_propaga_erro_http():
    with patch.object(anp_etl.requests, "get", return_value=_resp(500)), pytest.raises(requests.HTTPError):
        anp_etl._baixar("ca-2025-01")


# ---------- notify_failure
def _ctx():
    ti = SimpleNamespace(dag_id="anp", task_id="carga")
    return {"task_instance": ti, "run_id": "r1", "exception": "boom"}


def test_notify_failure_sem_webhook_so_registra_log(monkeypatch):
    monkeypatch.delenv("ALERT_WEBHOOK_URL", raising=False)
    with patch.object(anp_etl.requests, "post") as post:
        anp_etl.notify_failure(_ctx())
    post.assert_not_called()


def test_notify_failure_posta_no_webhook(monkeypatch):
    monkeypatch.setenv("ALERT_WEBHOOK_URL", "http://hook")
    with patch.object(anp_etl.requests, "post") as post:
        anp_etl.notify_failure(_ctx())
    corpo = post.call_args.kwargs["json"]
    assert "FALHA em anp.carga" in corpo["text"] and corpo["text"] == corpo["content"]


def test_notify_failure_engole_erro_do_webhook(monkeypatch):
    monkeypatch.setenv("ALERT_WEBHOOK_URL", "http://hook")
    with patch.object(anp_etl.requests, "post", side_effect=requests.ConnectionError("fora")):
        anp_etl.notify_failure(_ctx())  # não deve levantar


# ---------- publicar_evento_carga (mensageria)
def test_evento_nao_publicado_sem_url(monkeypatch):
    monkeypatch.setattr(anp_etl, "RABBITMQ_MGMT_URL", "")
    with patch.object(anp_etl.requests, "post") as post:
        assert anp_etl.publicar_evento_carga() is False
    post.assert_not_called()


def test_evento_publicado_no_exchange_com_chave_correta(monkeypatch):
    monkeypatch.setattr(anp_etl, "RABBITMQ_MGMT_URL", "http://mq:15672")
    resposta = MagicMock()
    resposta.json.return_value = {"routed": True}
    with patch.object(anp_etl.requests, "post", return_value=resposta) as post:
        assert anp_etl.publicar_evento_carga() is True
    assert post.call_args.args[0] == "http://mq:15672/api/exchanges/%2F/anp.eventos/publish"
    assert post.call_args.kwargs["json"]["routing_key"] == "carga.concluida"
    assert post.call_args.kwargs["auth"] == anp_etl.RABBITMQ_AUTH


def test_evento_sem_fila_ligada_retorna_false(monkeypatch):
    monkeypatch.setattr(anp_etl, "RABBITMQ_MGMT_URL", "http://mq:15672")
    resposta = MagicMock()
    resposta.json.return_value = {"routed": False}
    with patch.object(anp_etl.requests, "post", return_value=resposta):
        assert anp_etl.publicar_evento_carga() is False


def test_falha_de_mensageria_nao_quebra_a_carga(monkeypatch):
    monkeypatch.setattr(anp_etl, "RABBITMQ_MGMT_URL", "http://mq:15672")
    with patch.object(anp_etl.requests, "post", side_effect=requests.ConnectionError("fora")):
        assert anp_etl.publicar_evento_carga() is False
