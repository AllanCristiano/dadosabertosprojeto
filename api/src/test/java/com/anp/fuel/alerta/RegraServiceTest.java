package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anp.fuel.support.InMemoryRegraRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class RegraServiceTest {
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final InMemoryRegraRepository repo = new InMemoryRegraRepository();
    private final RegraService service = new RegraService(repo, RELOGIO);

    // ---- criar: RN01..RN04
    @Test void criaRegraNormalizandoProdutoEUf() {
        var r = service.criar("  gasolina comum ", " ac ", bd("5"));
        assertThat(r.id()).isNotNull();
        assertThat(r.produto()).isEqualTo(GASOLINA);
        assertThat(r.uf()).isEqualTo("AC");
        assertThat(r.status()).isEqualTo(StatusRegra.ATIVA);
        assertThat(r.criadaEm()).isEqualTo(AGORA);
    }

    @Test void produtoObrigatorio() {
        assertThatThrownBy(() -> service.criar(null, "AC", bd("5"))).isInstanceOf(AlertaException.Invalida.class);
        assertThatThrownBy(() -> service.criar("  ", "AC", bd("5"))).isInstanceOf(AlertaException.Invalida.class);
    }

    @Test void ufDeveSerValida() {
        assertThatThrownBy(() -> service.criar(GASOLINA, null, bd("5"))).hasMessageContaining("UF inválida");
        assertThatThrownBy(() -> service.criar(GASOLINA, "XX", bd("5"))).hasMessageContaining("UF inválida");
    }

    @Test void limiteDeveEstarEntreZeroExclusivoECem() {
        assertThatThrownBy(() -> service.criar(GASOLINA, "AC", null)).isInstanceOf(AlertaException.Invalida.class);
        assertThatThrownBy(() -> service.criar(GASOLINA, "AC", bd("0"))).isInstanceOf(AlertaException.Invalida.class);
        assertThatThrownBy(() -> service.criar(GASOLINA, "AC", bd("-1"))).isInstanceOf(AlertaException.Invalida.class);
        assertThatThrownBy(() -> service.criar(GASOLINA, "AC", bd("100.01"))).isInstanceOf(AlertaException.Invalida.class);
        assertThat(service.criar(GASOLINA, "AC", bd("100")).limitePct()).isEqualByComparingTo("100");
        assertThat(service.criar(GASOLINA, "SP", bd("0.01")).limitePct()).isEqualByComparingTo("0.01");
    }

    @Test void naoPermiteDuasRegrasAtivasParaOMesmoProdutoEUf() {
        service.criar(GASOLINA, "AC", bd("5"));
        assertThatThrownBy(() -> service.criar("gasolina comum", "ac", bd("8"))).isInstanceOf(AlertaException.Duplicada.class);
        assertThat(service.criar(GASOLINA, "SP", bd("5"))).isNotNull();
        assertThat(service.criar("ETANOL", "AC", bd("5"))).isNotNull();
    }

    @Test void limitaDezRegrasAtivas() {
        for (var uf : new String[] {"AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA"}) service.criar(GASOLINA, uf, bd("5"));
        assertThatThrownBy(() -> service.criar(GASOLINA, "MG", bd("5")))
            .isInstanceOf(AlertaException.Invalida.class).hasMessageContaining("10");
    }

    @Test void pausadasNaoContamNoLimiteNemNaUnicidade() {
        var r = service.criar(GASOLINA, "AC", bd("5"));
        service.pausar(r.id());
        assertThat(service.criar(GASOLINA, "AC", bd("7"))).isNotNull();
    }

    @Test void corridaDeCriacaoViraDuplicada() {
        var repoQueFalha = new InMemoryRegraRepository() {
            @Override public RegraAlerta criar(RegraAlerta r) { throw new DuplicateKeyException("ux_regra_ativa"); }
        };
        var s = new RegraService(repoQueFalha, RELOGIO);
        assertThatThrownBy(() -> s.criar(GASOLINA, "AC", bd("5"))).isInstanceOf(AlertaException.Duplicada.class);
    }

    // ---- consultar / ativar / pausar / remover: RN05
    @Test void listaEBusca() {
        var r = service.criar(GASOLINA, "AC", bd("5"));
        assertThat(service.listar()).containsExactly(r);
        assertThat(service.buscar(r.id())).isEqualTo(r);
        assertThatThrownBy(() -> service.buscar(99)).isInstanceOf(AlertaException.NaoEncontrada.class);
    }

    @Test void pausarEAtivarSaoIdempotentes() {
        var r = service.criar(GASOLINA, "AC", bd("5"));
        assertThat(service.ativar(r.id()).status()).isEqualTo(StatusRegra.ATIVA);
        assertThat(service.pausar(r.id()).status()).isEqualTo(StatusRegra.PAUSADA);
        assertThat(service.pausar(r.id()).status()).isEqualTo(StatusRegra.PAUSADA);
        assertThat(service.ativar(r.id()).status()).isEqualTo(StatusRegra.ATIVA);
        assertThat(service.buscar(r.id()).status()).isEqualTo(StatusRegra.ATIVA);
    }

    @Test void reativarRespeitaUnicidade() {
        var antiga = service.criar(GASOLINA, "AC", bd("5"));
        service.pausar(antiga.id());
        service.criar(GASOLINA, "AC", bd("9"));
        assertThatThrownBy(() -> service.ativar(antiga.id())).isInstanceOf(AlertaException.Duplicada.class);
    }

    @Test void reativarRespeitaLimiteDeDez() {
        var primeira = service.criar(GASOLINA, "AC", bd("5"));
        service.pausar(primeira.id());
        for (var uf : new String[] {"AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG"}) service.criar(GASOLINA, uf, bd("5"));
        assertThatThrownBy(() -> service.ativar(primeira.id())).isInstanceOf(AlertaException.Invalida.class);
    }

    @Test void corridaNaReativacaoViraDuplicada() {
        var repoQueFalha = new InMemoryRegraRepository() {
            @Override public void atualizarStatus(long id, StatusRegra s) { throw new DuplicateKeyException("ux_regra_ativa"); }
        };
        var pausada = repoQueFalha.criar(regra(null, "ETANOL", "AC", "5", StatusRegra.PAUSADA));
        var s = new RegraService(repoQueFalha, RELOGIO);
        assertThatThrownBy(() -> s.ativar(pausada.id())).isInstanceOf(AlertaException.Duplicada.class);
    }

    @Test void removeEFalhaSeNaoExiste() {
        var r = service.criar(GASOLINA, "AC", bd("5"));
        service.remover(r.id());
        assertThat(service.listar()).isEmpty();
        assertThatThrownBy(() -> service.remover(r.id())).isInstanceOf(AlertaException.NaoEncontrada.class);
        assertThatThrownBy(() -> service.ativar(r.id())).isInstanceOf(AlertaException.NaoEncontrada.class);
        assertThatThrownBy(() -> service.pausar(r.id())).isInstanceOf(AlertaException.NaoEncontrada.class);
    }
}
