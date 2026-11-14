package com.anp.fuel.support;

import com.anp.fuel.alerta.Alerta;
import com.anp.fuel.alerta.RegraAlerta;
import com.anp.fuel.alerta.Severidade;
import com.anp.fuel.alerta.StatusRegra;
import com.anp.fuel.analise.AnaliseDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class Fixtures {
    public static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");
    public static final LocalDate MES = LocalDate.of(2026, 6, 1);
    public static final String GASOLINA = "GASOLINA COMUM";
    private Fixtures() {}

    public static BigDecimal bd(String v) { return new BigDecimal(v); }

    public static RegraAlerta regra(Long id, String produto, String uf, String limite, StatusRegra status) {
        return new RegraAlerta(id, produto, uf, bd(limite), status, AGORA);
    }

    public static AnaliseDto linha(String uf, String produto, String variacao, boolean acima) {
        return new AnaliseDto(MES, uf, produto, bd("7.00"), bd("6.50"), variacao == null ? null : bd(variacao), acima);
    }

    public static Alerta alerta(Long regraId, LocalDate mes) {
        return new Alerta(null, regraId, GASOLINA, "AC", mes, bd("12.00"), bd("5.00"), Severidade.CRITICA, AGORA);
    }
}
