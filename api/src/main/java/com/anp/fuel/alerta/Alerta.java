package com.anp.fuel.alerta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Ocorrência de uma regra: a UF ultrapassou o limite em determinado mês. */
public record Alerta(Long id, Long regraId, String produto, String uf, LocalDate mesRef, BigDecimal variacaoPct,
                     BigDecimal limitePct, Severidade severidade, Instant criadoEm) {
    public Alerta comId(Long novoId) {
        return new Alerta(novoId, regraId, produto, uf, mesRef, variacaoPct, limitePct, severidade, criadoEm);
    }
}
