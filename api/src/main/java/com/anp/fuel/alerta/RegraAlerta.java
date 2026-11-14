package com.anp.fuel.alerta;

import java.math.BigDecimal;
import java.time.Instant;

/** Regra: "avise-me quando o preço de {produto} na {uf} passar {limitePct}% acima da média nacional". */
public record RegraAlerta(Long id, String produto, String uf, BigDecimal limitePct, StatusRegra status, Instant criadaEm) {
    public boolean ativa() { return status == StatusRegra.ATIVA; }
    public RegraAlerta comId(Long novoId) { return new RegraAlerta(novoId, produto, uf, limitePct, status, criadaEm); }
    public RegraAlerta comStatus(StatusRegra novo) { return new RegraAlerta(id, produto, uf, limitePct, novo, criadaEm); }
}
