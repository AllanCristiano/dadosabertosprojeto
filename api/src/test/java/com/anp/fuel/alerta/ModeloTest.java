package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModeloTest {
    @Test void regraAtivaEPausada() {
        var r = regra(1L, GASOLINA, "AC", "5", StatusRegra.ATIVA);
        assertThat(r.ativa()).isTrue();
        assertThat(r.comStatus(StatusRegra.PAUSADA).ativa()).isFalse();
        assertThat(r.comId(9L).id()).isEqualTo(9L);
        assertThat(r.comId(9L).produto()).isEqualTo(GASOLINA);
    }

    @Test void alertaComIdPreservaCampos() {
        var a = alerta(3L, MES).comId(7L);
        assertThat(a.id()).isEqualTo(7L);
        assertThat(a.regraId()).isEqualTo(3L);
        assertThat(a.severidade()).isEqualTo(Severidade.CRITICA);
    }

    @Test void mensagensDasExcecoes() {
        assertThat(new AlertaException.NaoEncontrada(4).getMessage()).contains("4");
        assertThat(new AlertaException.Duplicada("X", "SP").getMessage()).contains("X", "SP");
        assertThat(new AlertaException.Invalida("ruim").getMessage()).isEqualTo("ruim");
    }
}
