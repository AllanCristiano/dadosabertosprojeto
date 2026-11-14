package com.anp.fuel.alerta;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Regras de negócio do cadastro de alertas (RN01–RN05). */
@Service
public class RegraService {
    public static final int MAX_REGRAS_ATIVAS = 10;
    static final BigDecimal LIMITE_MAXIMO = new BigDecimal("100");
    static final Set<String> UFS = Set.of("AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT",
        "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    private final RegraRepository repo;
    private final Clock clock;
    public RegraService(RegraRepository repo, Clock clock) { this.repo = repo; this.clock = clock; }

    /** RN01 produto/UF obrigatórios e UF válida · RN02 limite em (0,100] · RN03 sem duplicata ativa · RN04 máx. 10 ativas. */
    public RegraAlerta criar(String produto, String uf, BigDecimal limitePct) {
        if (produto == null || produto.isBlank()) throw new AlertaException.Invalida("Produto é obrigatório");
        String p = produto.trim().toUpperCase();
        String u = uf == null ? "" : uf.trim().toUpperCase();
        if (!UFS.contains(u)) throw new AlertaException.Invalida("UF inválida: " + uf);
        if (limitePct == null || limitePct.signum() <= 0 || limitePct.compareTo(LIMITE_MAXIMO) > 0)
            throw new AlertaException.Invalida("Limite deve estar entre 0 (exclusivo) e 100");
        garantirPodeAtivar(p, u);
        try {
            return repo.criar(new RegraAlerta(null, p, u, limitePct, StatusRegra.ATIVA, clock.instant()));
        } catch (DuplicateKeyException e) {
            throw new AlertaException.Duplicada(p, u);
        }
    }

    public List<RegraAlerta> listar() { return repo.listar(); }

    public RegraAlerta buscar(long id) { return repo.buscar(id).orElseThrow(() -> new AlertaException.NaoEncontrada(id)); }

    /** RN05 reativar obedece às mesmas regras de unicidade e limite de regras ativas; ativar/pausar são idempotentes. */
    public RegraAlerta ativar(long id) {
        var regra = buscar(id);
        if (regra.ativa()) return regra;
        garantirPodeAtivar(regra.produto(), regra.uf());
        try {
            repo.atualizarStatus(id, StatusRegra.ATIVA);
        } catch (DuplicateKeyException e) {
            throw new AlertaException.Duplicada(regra.produto(), regra.uf());
        }
        return regra.comStatus(StatusRegra.ATIVA);
    }

    public RegraAlerta pausar(long id) {
        var regra = buscar(id);
        if (!regra.ativa()) return regra;
        repo.atualizarStatus(id, StatusRegra.PAUSADA);
        return regra.comStatus(StatusRegra.PAUSADA);
    }

    public void remover(long id) {
        if (!repo.remover(id)) throw new AlertaException.NaoEncontrada(id);
    }

    private void garantirPodeAtivar(String produto, String uf) {
        if (repo.existeAtiva(produto, uf)) throw new AlertaException.Duplicada(produto, uf);
        if (repo.contarAtivas() >= MAX_REGRAS_ATIVAS)
            throw new AlertaException.Invalida("Limite de " + MAX_REGRAS_ATIVAS + " regras ativas atingido");
    }
}
