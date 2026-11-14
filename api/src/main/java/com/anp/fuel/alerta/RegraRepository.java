package com.anp.fuel.alerta;

import java.util.List;
import java.util.Optional;

public interface RegraRepository {
    RegraAlerta criar(RegraAlerta regra);
    Optional<RegraAlerta> buscar(long id);
    List<RegraAlerta> listar();
    List<RegraAlerta> listarAtivas();
    boolean existeAtiva(String produto, String uf);
    long contarAtivas();
    void atualizarStatus(long id, StatusRegra status);
    boolean remover(long id);
}
