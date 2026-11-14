package com.anp.fuel.alerta;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Gravidade do alerta, medida por quantas vezes a variação excede o limite configurado (RN07). */
public enum Severidade {
    ATENCAO, ALTA, CRITICA;

    private static final BigDecimal LIMIAR_ALTA = new BigDecimal("1.5");
    private static final BigDecimal LIMIAR_CRITICA = new BigDecimal("2");

    public static Severidade de(BigDecimal variacaoPct, BigDecimal limitePct) {
        BigDecimal razao = variacaoPct.divide(limitePct, 4, RoundingMode.HALF_UP);
        if (razao.compareTo(LIMIAR_CRITICA) >= 0) return CRITICA;
        if (razao.compareTo(LIMIAR_ALTA) >= 0) return ALTA;
        return ATENCAO;
    }
}
