-- Módulo de alertas: regras de preço configuradas pelo usuário e ocorrências disparadas.
CREATE SCHEMA IF NOT EXISTS alerta;

CREATE TABLE IF NOT EXISTS alerta.regra (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    produto    text         NOT NULL,
    uf         char(2)      NOT NULL,
    limite_pct numeric(5,2) NOT NULL CHECK (limite_pct > 0 AND limite_pct <= 100),   -- RN02
    status     text         NOT NULL CHECK (status IN ('ATIVA', 'PAUSADA')),
    criada_em  timestamptz  NOT NULL
);
-- RN03: no máximo uma regra ativa por produto/UF (também vale sob concorrência).
CREATE UNIQUE INDEX IF NOT EXISTS ux_regra_ativa ON alerta.regra (produto, uf) WHERE status = 'ATIVA';

CREATE TABLE IF NOT EXISTS alerta.alerta_disparado (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regra_id     bigint       NOT NULL REFERENCES alerta.regra ON DELETE CASCADE,
    produto      text         NOT NULL,
    uf           char(2)      NOT NULL,
    mes_ref      date         NOT NULL,
    variacao_pct numeric(8,2) NOT NULL,
    limite_pct   numeric(5,2) NOT NULL,
    severidade   text         NOT NULL CHECK (severidade IN ('ATENCAO', 'ALTA', 'CRITICA')),
    criado_em    timestamptz  NOT NULL,
    UNIQUE (regra_id, mes_ref)                                                        -- RN06: um alerta por regra/mês
);
