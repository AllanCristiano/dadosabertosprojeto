package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.bd;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeveridadeTest {
    @Test void abaixoDeUmEMeioVezesOLimiteEhAtencao() {
        assertThat(Severidade.de(bd("5"), bd("5"))).isEqualTo(Severidade.ATENCAO);
        assertThat(Severidade.de(bd("7.4"), bd("5"))).isEqualTo(Severidade.ATENCAO);
    }

    @Test void deUmEMeioAteDuasVezesEhAlta() {
        assertThat(Severidade.de(bd("7.5"), bd("5"))).isEqualTo(Severidade.ALTA);
        assertThat(Severidade.de(bd("9.9"), bd("5"))).isEqualTo(Severidade.ALTA);
    }

    @Test void duasVezesOuMaisEhCritica() {
        assertThat(Severidade.de(bd("10"), bd("5"))).isEqualTo(Severidade.CRITICA);
        assertThat(Severidade.de(bd("40"), bd("5"))).isEqualTo(Severidade.CRITICA);
    }
}
