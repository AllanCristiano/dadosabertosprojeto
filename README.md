# ⛽ Pulso dos Combustíveis

> **Pergunta de negócio:** *em quais UFs o preço do combustível está acima da média nacional — e como isso evoluiu mês a mês?*

Pipeline de dados ponta a ponta sobre a **série histórica de preços de combustíveis da ANP** (dados abertos, cobertura nacional):
**Airflow (ETL) → PostgreSQL (modelo dimensional) → API Spring Boot → painel Angular mobile-first → Power BI**.

![demo](docs/painel-demo.gif)

## Resposta à pergunta de negócio

Exemplo com os dados carregados (gasolina, junho/2026): **18 das 27 UFs** estavam acima da média nacional (R$ 6,66). As maiores altas: **RR +15,3%**, **AC +12,0%** e **RO +10,5%**; as menores, **MG −4,4%** e **RS −3,2%**. O painel permite trocar o produto, tocar numa UF e ver sua série mensal contra o Brasil.

## Telas

| Login | Painel (desktop) | Explorar | Painel (mobile) |
|---|---|---|---|
| ![login](docs/login.jpg) | ![painel](docs/painel.jpg) | ![explorar](docs/explorar.jpg) | ![mobile](docs/painel-mobile.jpg) |

## Executar

Pré-requisito: Docker. Portas usadas: `4200`, `8080`, `8081`, `5433`.

```bash
docker compose up -d --build                                   # 1. sobe tudo
docker compose exec airflow-scheduler airflow dags unpause anp_combustiveis_etl   # 2. ativa a DAG
docker compose exec airflow-scheduler airflow dags trigger anp_combustiveis_etl   # 3. carrega os dados (~3 min)
```

| Serviço | URL | Acesso |
|---|---|---|
| Painel Angular | http://localhost:4200 | `demo` / `demo` |
| API + Swagger | http://localhost:8081/swagger-ui.html | login em `POST /api/v1/auth/login` |
| Airflow | http://localhost:8080 | `admin` / `admin` |
| PostgreSQL | `localhost:5433` (db `anp`, schema `dw`) | `anp` / `anp` |

> A DAG nasce pausada (padrão do Airflow); também pode ser ativada pela interface. Credenciais acima são **apenas de demonstração**; em outro ambiente defina `JWT_SECRET`, `APP_USER` e `APP_PASSWORD`.

## Arquitetura

```mermaid
flowchart LR
  ANP[(ANP dados abertos<br/>zip/csv semestral)] --> AF[Airflow DAG<br/>extrai · valida · limpa]
  AF --> ST[(staging)]
  ST --> DW[(PostgreSQL<br/>modelo dimensional)]
  DW --> API[Spring Boot API<br/>JWT · paginação · Swagger]
  API --> NG[Angular<br/>painel mobile-first]
  DW --> PBI[Power BI]
```

| Camada | Pasta | Tecnologia |
|---|---|---|
| ETL | `airflow/dags` | Python 3.12 + Airflow 2.10 |
| Banco | `db/init` | PostgreSQL 16 (star schema, matview, view analítica) |
| API | `api` | Java 21 + Spring Boot 3.4 — monolito modular: `auth`, `preco`, `analise`, `shared` |
| Painel | `web` | Angular 22 — monolito modular: `core`, `shared`, `layout`, `features/*` (rotas lazy) |
| Power BI | `powerbi` | Guia de conexão + medidas DAX |
| CI | `.github/workflows` | GitHub Actions: testes e build da API, do painel e checagem da DAG |

> 📐 Documento completo de arquitetura (atores, casos de uso, processos, visões C4, dados, implantação, ADRs, segurança e riscos): [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md)

## Modelo de dados

```
dim_data ─┐
dim_produto ─┤
dim_localidade ─┼── fato_preco (data × revenda × produto → preco_venda, preco_compra)
dim_revenda ── dim_bandeira
```

- `dw.mv_preco_mensal_uf`: agregado mensal por UF/produto (materializado, atualizado ao fim da DAG).
- `dw.vw_preco_vs_media_nacional`: view pronta para análise, compara cada UF com a média nacional **ponderada** do mês (`variacao_pct`, `acima_da_media`).
- Índices em `fato_preco (produto_id, data_id)` e `(localidade_id, produto_id)`.

## Decisões de projeto

- **Airflow, não cron:** retries, observabilidade por task, *dynamic task mapping* (um arquivo = uma task) e alerta de falha.
- **Modelo dimensional:** grão = coleta por revenda × produto × dia; consultas analíticas ficam simples e rápidas.
- **Carga incremental:** baixa só semestres ausentes em `dw.etl_controle` mais o semestre corrente (a ANP republica o arquivo vigente). Parâmetro `force=true` reprocessa tudo.
- **Idempotência:** staging é limpo por arquivo antes da carga; dimensões e fato usam `ON CONFLICT … DO NOTHING/UPDATE` na chave natural. **Verificado:** reexecutar com `force` manteve exatamente 1.227.029 linhas na fato.
- **Qualidade de dados:** colunas obrigatórias, faixa de preço (R$ 0,50–30), UF/CNPJ/data válidos; linhas ruins são descartadas e contadas, e a task **falha** se a rejeição passar de 5%. Há checagem pós-carga (fato vazia ou órfã) antes de considerar o run saudável — na primeira execução ela barrou corretamente uma carga vazia.
- **Alerta:** `on_failure_callback` loga e, se `ALERT_WEBHOOK_URL` estiver definido, posta em Slack/Discord/Teams.
- **Monolito modular:** um deploy por camada, com fronteiras claras por módulo (pacotes na API, features lazy no Angular).
- **Auth simples:** login → JWT HS256 stateless. Suficiente para demo; em produção, usar um provedor de identidade.

## Experiência do painel (mobile-first)

Entrada cinematográfica palavra a palavra, fundo em aurora animada, vidro fosco, *scroll reveal* com blur, contadores animados, barras que crescem em cascata, transições de rota via View Transitions API, gráficos com animação e tab bar inferior estilo app. Respeita `prefers-reduced-motion`. Exporta CSV (ranking e coletas).

## Testes e qualidade

| Alvo | Comando | Resultado |
|---|---|---|
| API (contrato HTTP + segurança, sem banco) | `cd api && mvn verify` | 6 testes |
| Painel | `cd web && npx ng test --watch=false` | 2 testes (exportação CSV) |
| ETL | execução real da DAG | 1,2 mi linhas carregadas, idempotência confirmada |

O CI roda os mesmos passos a cada push/PR.

## Power BI

Conexão em `localhost:5433`, banco `anp`, view `dw.vw_preco_vs_media_nacional`. Passo a passo, medidas DAX e páginas sugeridas em [`powerbi/README.md`](powerbi/README.md).

> 🚧 O arquivo `.pbix` e suas capturas ainda não foram adicionados: só podem ser gerados no Power BI Desktop.

## Desenvolvimento local

```bash
docker compose up -d postgres api      # banco e API (API em :8081)
cd web && npm i && npm start           # painel em :4200, com proxy /api → :8081
cd api && mvn verify                   # testes da API
```

## Fonte dos dados

[ANP — Série histórica de preços de combustíveis](https://www.gov.br/anp/pt-br/centrais-de-conteudo/dados-abertos/serie-historica-de-precos-de-combustiveis), dados abertos. A ANP publica `.zip` por semestre (2022 em diante) e `.csv` nos anos anteriores; a DAG trata os dois formatos. Variáveis: `ANP_BASE_URL` e `ANP_FIRST_YEAR` (padrão `2025`, para um demo rápido; use `2023` para mais histórico).
