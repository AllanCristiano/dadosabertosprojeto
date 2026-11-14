package com.anp.fuel.alerta;

import java.util.List;
import java.util.Optional;

public interface AlertaRepository {
    /** Grava o alerta; devolve vazio se a regra já alertou aquele mês (RN06 — idempotência). */
    Optional<Alerta> registrar(Alerta alerta);
    List<Alerta> recentes(int limite);
}
