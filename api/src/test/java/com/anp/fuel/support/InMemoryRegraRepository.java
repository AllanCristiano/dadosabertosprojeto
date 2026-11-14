package com.anp.fuel.support;

import com.anp.fuel.alerta.RegraAlerta;
import com.anp.fuel.alerta.RegraRepository;
import com.anp.fuel.alerta.StatusRegra;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Dublê em memória: permite testar as regras de negócio sem banco. */
public class InMemoryRegraRepository implements RegraRepository {
    private final List<RegraAlerta> dados = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override public RegraAlerta criar(RegraAlerta r) {
        var salva = r.comId(seq.incrementAndGet());
        dados.add(salva);
        return salva;
    }
    @Override public Optional<RegraAlerta> buscar(long id) { return dados.stream().filter(r -> r.id() == id).findFirst(); }
    @Override public List<RegraAlerta> listar() { return List.copyOf(dados); }
    @Override public List<RegraAlerta> listarAtivas() { return dados.stream().filter(RegraAlerta::ativa).toList(); }
    @Override public boolean existeAtiva(String produto, String uf) {
        return listarAtivas().stream().anyMatch(r -> r.produto().equals(produto) && r.uf().equals(uf));
    }
    @Override public long contarAtivas() { return listarAtivas().size(); }
    @Override public void atualizarStatus(long id, StatusRegra status) {
        dados.replaceAll(r -> r.id() == id ? r.comStatus(status) : r);
    }
    @Override public boolean remover(long id) { return dados.removeIf(r -> r.id() == id); }
}
