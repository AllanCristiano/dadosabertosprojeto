package com.anp.fuel;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.anp.fuel.alerta.Alerta;
import com.anp.fuel.alerta.AvaliacaoService;
import com.anp.fuel.alerta.JdbcAlertaRepository;
import com.anp.fuel.alerta.JdbcRegraRepository;
import com.anp.fuel.alerta.Mensageria;
import com.anp.fuel.alerta.RegraService;
import com.anp.fuel.alerta.Severidade;
import com.anp.fuel.alerta.StatusRegra;
import com.anp.fuel.analise.AnaliseRepository;
import com.anp.fuel.preco.PrecoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Repositórios contra um banco real (H2 em memória) com o esquema de {@code src/test/resources/schema.sql}. */
@JdbcTest
@AutoConfigureTestDatabase
@Import({PrecoRepository.class, AnaliseRepository.class, JdbcRegraRepository.class, JdbcAlertaRepository.class})
class RepositoriosJdbcTest {
    @Autowired JdbcClient jdbc;
    @Autowired PrecoRepository precos;
    @Autowired AnaliseRepository analise;
    @Autowired JdbcRegraRepository regras;
    @Autowired JdbcAlertaRepository alertas;

    @BeforeEach void semear() {
        jdbc.sql("INSERT INTO dw.dim_data VALUES (20260601, DATE '2026-06-01'), (20260615, DATE '2026-06-15')").update();
        jdbc.sql("INSERT INTO dw.dim_produto VALUES (1, 'GASOLINA COMUM'), (2, 'ETANOL HIDRATADO')").update();
        jdbc.sql("INSERT INTO dw.dim_localidade VALUES (1, 'N', 'AC', 'RIO BRANCO'), (2, 'SE', 'SP', 'SAO PAULO')").update();
        jdbc.sql("INSERT INTO dw.dim_bandeira VALUES (1, 'BR')").update();
        jdbc.sql("INSERT INTO dw.dim_revenda VALUES (1, '11', 'POSTO A', 1), (2, '22', 'POSTO B', NULL)").update();
        jdbc.sql("INSERT INTO dw.fato_preco VALUES "
            + "(20260601, 1, 1, 1, 7.500, 6.000), (20260615, 1, 1, 1, 7.600, NULL), "
            + "(20260615, 2, 1, 2, 6.100, NULL), (20260615, 2, 2, 2, 4.200, NULL)").update();
        jdbc.sql("INSERT INTO dw.vw_preco_vs_media_nacional VALUES "
            + "(DATE '2026-05-01', 'AC', 'GASOLINA COMUM', 10, 7.000, 6.500, 7.69, TRUE), "
            + "(DATE '2026-06-01', 'AC', 'GASOLINA COMUM', 10, 7.500, 6.500, 15.38, TRUE), "
            + "(DATE '2026-06-01', 'SP', 'GASOLINA COMUM', 10, 6.100, 6.500, -6.15, FALSE)").update();
    }

    // ---------- PrecoRepository
    @Test void buscaSemFiltroOrdenaPorDataDesc() {
        var todos = precos.buscar(new PrecoRepository.Filtro(null, null, null, null, null), 10, 0);
        assertThat(todos).hasSize(4);
        assertThat(todos.get(0).data()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(precos.contar(new PrecoRepository.Filtro(null, null, null, null, null))).isEqualTo(4);
    }

    @Test void combinaTodosOsFiltros() {
        var f = new PrecoRepository.Filtro("gasolina comum", "ac", "rio branco", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 30));
        var r = precos.buscar(f, 10, 0);
        assertThat(r).singleElement().satisfies(p -> {
            assertThat(p.uf()).isEqualTo("AC");
            assertThat(p.revenda()).isEqualTo("POSTO A");
            assertThat(p.bandeira()).isEqualTo("BR");
            assertThat(p.precoVenda()).isEqualByComparingTo("7.600");
            assertThat(p.precoCompra()).isNull();
        });
        assertThat(precos.contar(f)).isEqualTo(1);
    }

    @Test void filtrosIndividuaisEPaginacao() {
        assertThat(precos.contar(new PrecoRepository.Filtro("ETANOL HIDRATADO", null, null, null, null))).isEqualTo(1);
        assertThat(precos.contar(new PrecoRepository.Filtro(null, "SP", null, null, null))).isEqualTo(2);
        assertThat(precos.contar(new PrecoRepository.Filtro(null, null, "SAO PAULO", null, null))).isEqualTo(2);
        assertThat(precos.contar(new PrecoRepository.Filtro(null, null, null, LocalDate.of(2026, 6, 15), null))).isEqualTo(3);
        assertThat(precos.contar(new PrecoRepository.Filtro(null, null, null, null, LocalDate.of(2026, 6, 1)))).isEqualTo(1);
        var f = new PrecoRepository.Filtro(null, null, null, null, null);
        assertThat(precos.buscar(f, 3, 0)).hasSize(3);
        assertThat(precos.buscar(f, 3, 3)).hasSize(1);
        var semBandeira = precos.buscar(new PrecoRepository.Filtro(null, "SP", null, null, null), 10, 0);
        assertThat(semBandeira).allSatisfy(p -> assertThat(p.bandeira()).isNull());
    }

    // ---------- AnaliseRepository
    @Test void analiseUltimoMesRankingSerieEListas() {
        assertThat(analise.ultimoMes("GASOLINA COMUM")).contains(LocalDate.of(2026, 6, 1));
        assertThat(analise.ultimoMes("PRODUTO INEXISTENTE")).isEmpty();
        var ranking = analise.ranking("GASOLINA COMUM", LocalDate.of(2026, 6, 1));
        assertThat(ranking).extracting(a -> a.uf()).containsExactly("AC", "SP");
        assertThat(ranking.get(0).acimaDaMedia()).isTrue();
        assertThat(ranking.get(1).variacaoPct()).isEqualByComparingTo("-6.15");
        assertThat(analise.serie("GASOLINA COMUM", "AC")).extracting(a -> a.mesRef())
            .containsExactly(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));
        assertThat(analise.produtos()).containsExactly("ETANOL HIDRATADO", "GASOLINA COMUM");
        assertThat(analise.ufs()).containsExactly("AC", "SP");
    }

    // ---------- JdbcRegraRepository
    @Test void regraCicloCompleto() {
        var criada = regras.criar(regra(null, GASOLINA, "AC", "5.50", StatusRegra.ATIVA));
        assertThat(criada.id()).isNotNull();
        var lida = regras.buscar(criada.id()).orElseThrow();
        assertThat(lida.limitePct()).isEqualByComparingTo("5.50");
        assertThat(lida.criadaEm()).isEqualTo(AGORA);
        assertThat(regras.buscar(-1)).isEmpty();

        assertThat(regras.existeAtiva(GASOLINA, "AC")).isTrue();
        assertThat(regras.existeAtiva(GASOLINA, "SP")).isFalse();
        assertThat(regras.contarAtivas()).isEqualTo(1);

        regras.atualizarStatus(criada.id(), StatusRegra.PAUSADA);
        assertThat(regras.existeAtiva(GASOLINA, "AC")).isFalse();
        assertThat(regras.contarAtivas()).isZero();
        assertThat(regras.listarAtivas()).isEmpty();
        assertThat(regras.listar()).singleElement().extracting(r -> r.status()).isEqualTo(StatusRegra.PAUSADA);

        assertThat(regras.remover(criada.id())).isTrue();
        assertThat(regras.remover(criada.id())).isFalse();
    }

    // ---------- JdbcAlertaRepository
    @Test void alertaRegistraDeduplicaEListaRecentes() {
        var regra = regras.criar(regra(null, GASOLINA, "AC", "5", StatusRegra.ATIVA));
        var primeiro = alertas.registrar(alerta(regra.id(), LocalDate.of(2026, 5, 1)));
        assertThat(primeiro).isPresent();
        assertThat(primeiro.get().id()).isNotNull();
        assertThat(alertas.registrar(alerta(regra.id(), LocalDate.of(2026, 5, 1)))).isEmpty();
        alertas.registrar(alerta(regra.id(), LocalDate.of(2026, 6, 1)));

        List<Alerta> recentes = alertas.recentes(10);
        assertThat(recentes).hasSize(2);
        assertThat(recentes.get(0).mesRef()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(recentes.get(0).severidade()).isEqualTo(Severidade.CRITICA);
        assertThat(recentes.get(0).criadoEm()).isEqualTo(AGORA);
        assertThat(alertas.recentes(1)).hasSize(1);
    }

    // ---------- Fluxo completo com banco: regra → avaliação → alerta persistido e publicado
    @Test void fluxoRegraAvaliacaoAlertaComBancoReal() {
        var clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        var service = new RegraService(regras, clock);
        service.criar("gasolina comum", "ac", bd("10"));   // AC está 15,38% acima → dispara (ALTA)
        service.criar("gasolina comum", "sp", bd("1"));    // SP está abaixo da média → não dispara
        var publicados = new ArrayList<Alerta>();
        Mensageria fila = new Mensageria() {
            @Override public void publicarCarga(com.anp.fuel.alerta.EventoCargaConcluida e) { }
            @Override public void publicarAlerta(Alerta a) { publicados.add(a); }
        };
        var avaliacao = new AvaliacaoService(regras, alertas, analise, fila, clock);

        assertThat(avaliacao.avaliar(null)).isEqualTo(1);
        assertThat(avaliacao.avaliar(null)).isZero();   // idempotente: mesmo mês não repete

        assertThat(publicados).singleElement().satisfies(a -> {
            assertThat(a.uf()).isEqualTo("AC");
            assertThat(a.severidade()).isEqualTo(Severidade.ALTA);
        });
        assertThat(alertas.recentes(10)).hasSize(1);
    }
}
