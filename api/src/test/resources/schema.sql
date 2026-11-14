-- Esquema mínimo para testes de repositório em H2 (a view analítica vira tabela; o índice parcial não existe aqui).
CREATE SCHEMA IF NOT EXISTS dw;
CREATE SCHEMA IF NOT EXISTS alerta;

CREATE TABLE dw.dim_data (data_id integer PRIMARY KEY, data date NOT NULL);
CREATE TABLE dw.dim_produto (produto_id integer PRIMARY KEY, nome varchar(100) NOT NULL);
CREATE TABLE dw.dim_localidade (localidade_id integer PRIMARY KEY, regiao varchar(20), uf char(2) NOT NULL, municipio varchar(100) NOT NULL);
CREATE TABLE dw.dim_bandeira (bandeira_id integer PRIMARY KEY, nome varchar(100) NOT NULL);
CREATE TABLE dw.dim_revenda (revenda_id integer PRIMARY KEY, cnpj varchar(20) NOT NULL, nome varchar(100), bandeira_id integer);
CREATE TABLE dw.fato_preco (
    data_id integer NOT NULL, revenda_id integer NOT NULL, produto_id integer NOT NULL, localidade_id integer NOT NULL,
    preco_venda numeric(8,3) NOT NULL, preco_compra numeric(8,3));
CREATE TABLE dw.vw_preco_vs_media_nacional (
    mes_ref date NOT NULL, uf char(2) NOT NULL, produto varchar(100) NOT NULL, coletas integer,
    preco_medio_uf numeric(8,3), preco_medio_nacional numeric(8,3), variacao_pct numeric(8,2), acima_da_media boolean);

CREATE TABLE alerta.regra (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, produto varchar(100) NOT NULL, uf char(2) NOT NULL,
    limite_pct numeric(5,2) NOT NULL, status varchar(10) NOT NULL, criada_em timestamp with time zone NOT NULL);
CREATE TABLE alerta.alerta_disparado (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, regra_id bigint NOT NULL REFERENCES alerta.regra ON DELETE CASCADE,
    produto varchar(100) NOT NULL, uf char(2) NOT NULL, mes_ref date NOT NULL, variacao_pct numeric(8,2) NOT NULL,
    limite_pct numeric(5,2) NOT NULL, severidade varchar(10) NOT NULL, criado_em timestamp with time zone NOT NULL,
    UNIQUE (regra_id, mes_ref));
