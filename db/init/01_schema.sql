-- Modelo dimensional (star schema) para a série histórica de preços de combustíveis da ANP.
-- Executado no primeiro boot do Postgres (docker-entrypoint-initdb.d).

CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS dw;

-- ---------- Controle de carga incremental ----------
CREATE TABLE IF NOT EXISTS dw.etl_controle (
    arquivo        text PRIMARY KEY,           -- ex.: ca-2024-01
    linhas_lidas   integer NOT NULL,
    linhas_validas integer NOT NULL,
    carregado_em   timestamptz NOT NULL DEFAULT now()
);

-- ---------- Staging (dado "cru" já tipado) ----------
CREATE TABLE IF NOT EXISTS staging.stg_preco (
    arquivo       text NOT NULL,
    regiao        text,
    uf            char(2) NOT NULL,
    municipio     text NOT NULL,
    cnpj          text NOT NULL,
    revenda       text,
    produto       text NOT NULL,
    bandeira      text,
    data_coleta   date NOT NULL,
    preco_venda   numeric(8,3) NOT NULL,
    preco_compra  numeric(8,3)
);
CREATE INDEX IF NOT EXISTS ix_stg_arquivo ON staging.stg_preco (arquivo);

-- ---------- Dimensões ----------
CREATE TABLE IF NOT EXISTS dw.dim_data (
    data_id   integer PRIMARY KEY,             -- yyyymmdd
    data      date NOT NULL UNIQUE,
    ano       smallint NOT NULL,
    trimestre smallint NOT NULL,
    mes       smallint NOT NULL,
    semana    smallint NOT NULL
);

CREATE TABLE IF NOT EXISTS dw.dim_produto (
    produto_id serial PRIMARY KEY,
    nome       text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS dw.dim_localidade (
    localidade_id serial PRIMARY KEY,
    regiao        text,
    uf            char(2) NOT NULL,
    municipio     text NOT NULL,
    UNIQUE (uf, municipio)
);

CREATE TABLE IF NOT EXISTS dw.dim_bandeira (
    bandeira_id serial PRIMARY KEY,
    nome        text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS dw.dim_revenda (
    revenda_id    serial PRIMARY KEY,
    cnpj          text NOT NULL UNIQUE,
    nome          text,
    bandeira_id   integer REFERENCES dw.dim_bandeira
);

-- ---------- Fato ----------
-- Grão: uma coleta de preço por revenda x produto x dia.
-- A PK natural garante idempotência (ON CONFLICT DO UPDATE no reprocessamento).
CREATE TABLE IF NOT EXISTS dw.fato_preco (
    data_id       integer  NOT NULL REFERENCES dw.dim_data,
    revenda_id    integer  NOT NULL REFERENCES dw.dim_revenda,
    produto_id    integer  NOT NULL REFERENCES dw.dim_produto,
    localidade_id integer  NOT NULL REFERENCES dw.dim_localidade,
    preco_venda   numeric(8,3) NOT NULL CHECK (preco_venda > 0),
    preco_compra  numeric(8,3),
    PRIMARY KEY (data_id, revenda_id, produto_id)
);
CREATE INDEX IF NOT EXISTS ix_fato_produto_data ON dw.fato_preco (produto_id, data_id);
CREATE INDEX IF NOT EXISTS ix_fato_localidade   ON dw.fato_preco (localidade_id, produto_id);

-- ---------- Camada analítica ----------
-- Agregado mensal por UF/produto (materializado: barato de ler, refresh ao fim da DAG).
CREATE MATERIALIZED VIEW IF NOT EXISTS dw.mv_preco_mensal_uf AS
SELECT date_trunc('month', d.data)::date AS mes_ref,
       l.uf,
       p.nome                            AS produto,
       avg(f.preco_venda)::numeric(8,4)  AS preco_medio,
       count(*)                          AS coletas
FROM dw.fato_preco f
JOIN dw.dim_data       d ON d.data_id       = f.data_id
JOIN dw.dim_localidade l ON l.localidade_id = f.localidade_id
JOIN dw.dim_produto    p ON p.produto_id    = f.produto_id
GROUP BY 1, 2, 3
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_mensal ON dw.mv_preco_mensal_uf (mes_ref, uf, produto);

-- View pronta para análise: cada UF contra a média nacional do mesmo mês/produto.
CREATE OR REPLACE VIEW dw.vw_preco_vs_media_nacional AS
WITH base AS (
    SELECT m.*,
           sum(preco_medio * coletas) OVER w / sum(coletas) OVER w AS preco_medio_nacional
    FROM dw.mv_preco_mensal_uf m
    WINDOW w AS (PARTITION BY mes_ref, produto)
)
SELECT mes_ref, uf, produto, coletas,
       round(preco_medio, 3)                                              AS preco_medio_uf,
       round(preco_medio_nacional, 3)                                     AS preco_medio_nacional,
       round((preco_medio / preco_medio_nacional - 1) * 100, 2)           AS variacao_pct,
       preco_medio > preco_medio_nacional                                 AS acima_da_media
FROM base;
