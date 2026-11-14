package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AvaliadorAlertasTest {
    private final RegraAlerta regraAc = regra(1L, GASOLINA, "AC", "5", StatusRegra.ATIVA);

    @Test void disparaQuandoAcimaDoLimite() {
        var r = AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, "12.00", true)), List.of(regraAc), AGORA);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).regraId()).isEqualTo(1L);
        assertThat(r.get(0).severidade()).isEqualTo(Severidade.CRITICA);
        assertThat(r.get(0).mesRef()).isEqualTo(MES);
        assertThat(r.get(0).criadoEm()).isEqualTo(AGORA);
    }

    @Test void limiteExatoDispara() {
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, "5.00", true)), List.of(regraAc), AGORA)).hasSize(1);
    }

    @Test void abaixoDoLimiteNaoDispara() {
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, "4.99", true)), List.of(regraAc), AGORA)).isEmpty();
    }

    @Test void foraDaAcimaDaMediaNaoDispara() {
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, "9.00", false)), List.of(regraAc), AGORA)).isEmpty();
    }

    @Test void variacaoNulaNaoDispara() {
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, null, true)), List.of(regraAc), AGORA)).isEmpty();
    }

    @Test void outraUfOuOutroProdutoNaoDispara() {
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("SP", GASOLINA, "20", true)), List.of(regraAc), AGORA)).isEmpty();
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", "DIESEL S10", "20", true)), List.of(regraAc), AGORA)).isEmpty();
    }

    @Test void regraPausadaNaoDispara() {
        var pausada = regraAc.comStatus(StatusRegra.PAUSADA);
        assertThat(AvaliadorAlertas.avaliar(List.of(linha("AC", GASOLINA, "20", true)), List.of(pausada), AGORA)).isEmpty();
    }

    @Test void variasRegrasGeramVariosAlertas() {
        var sp = regra(2L, GASOLINA, "SP", "1", StatusRegra.ATIVA);
        var ranking = List.of(linha("AC", GASOLINA, "6", true), linha("SP", GASOLINA, "2", true));
        assertThat(AvaliadorAlertas.avaliar(ranking, List.of(regraAc, sp), AGORA)).hasSize(2);
    }
}
