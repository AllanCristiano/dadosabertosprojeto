package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anp.fuel.analise.AnaliseRepository;
import com.anp.fuel.support.InMemoryAlertaRepository;
import com.anp.fuel.support.InMemoryRegraRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvaliacaoServiceTest {
    private final InMemoryRegraRepository regras = new InMemoryRegraRepository();
    private final InMemoryAlertaRepository alertas = new InMemoryAlertaRepository();
    private final AnaliseRepository analise = mock(AnaliseRepository.class);
    private final Mensageria mensageria = mock(Mensageria.class);
    private final AvaliacaoService service =
        new AvaliacaoService(regras, alertas, analise, mensageria, Clock.fixed(AGORA, ZoneOffset.UTC));

    @BeforeEach void rankingPadrao() {
        when(analise.ultimoMes(GASOLINA)).thenReturn(Optional.of(MES));
        when(analise.ranking(GASOLINA, MES)).thenReturn(List.of(
            linha("AC", GASOLINA, "12.00", true), linha("SP", GASOLINA, "1.00", true), linha("MG", GASOLINA, "-4", false)));
    }

    @Test void semRegrasNaoFazNada() {
        assertThat(service.avaliar(null)).isZero();
        verify(mensageria, never()).publicarAlerta(any());
    }

    @Test void mesNuloUsaOMaisRecenteEPublicaSoOsQueDisparam() {
        regras.criar(regra(null, GASOLINA, "AC", "5", StatusRegra.ATIVA));
        regras.criar(regra(null, GASOLINA, "SP", "5", StatusRegra.ATIVA));
        assertThat(service.avaliar(null)).isEqualTo(1);
        verify(mensageria, times(1)).publicarAlerta(any());
        assertThat(alertas.recentes(10)).singleElement().satisfies(a -> {
            assertThat(a.uf()).isEqualTo("AC");
            assertThat(a.id()).isNotNull();
        });
    }

    @Test void mesExplicitoEhNormalizadoParaDiaUm() {
        regras.criar(regra(null, GASOLINA, "AC", "5", StatusRegra.ATIVA));
        assertThat(service.avaliar(LocalDate.of(2026, 6, 20))).isEqualTo(1);
        verify(analise, never()).ultimoMes(any());
    }

    @Test void reavaliarOMesmoMesNaoDuplicaAlerta() {
        regras.criar(regra(null, GASOLINA, "AC", "5", StatusRegra.ATIVA));
        assertThat(service.avaliar(null)).isEqualTo(1);
        assertThat(service.avaliar(null)).isZero();
        verify(mensageria, times(1)).publicarAlerta(any());
    }

    @Test void produtoSemDadosEhIgnorado() {
        regras.criar(regra(null, "ETANOL", "AC", "5", StatusRegra.ATIVA));
        when(analise.ultimoMes("ETANOL")).thenReturn(Optional.empty());
        assertThat(service.avaliar(null)).isZero();
        verify(analise, never()).ranking(any(), any());
    }

    @Test void regraPausadaNaoEhAvaliada() {
        regras.criar(regra(null, GASOLINA, "AC", "5", StatusRegra.PAUSADA));
        assertThat(service.avaliar(null)).isZero();
    }
}
