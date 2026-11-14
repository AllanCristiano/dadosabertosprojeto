package com.anp.fuel.support;

import com.anp.fuel.alerta.Alerta;
import com.anp.fuel.alerta.AlertaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryAlertaRepository implements AlertaRepository {
    private final List<Alerta> dados = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override public Optional<Alerta> registrar(Alerta a) {
        boolean jaExiste = dados.stream().anyMatch(x -> x.regraId().equals(a.regraId()) && x.mesRef().equals(a.mesRef()));
        if (jaExiste) return Optional.empty();
        var salvo = a.comId(seq.incrementAndGet());
        dados.add(salvo);
        return Optional.of(salvo);
    }
    @Override public List<Alerta> recentes(int limite) {
        return dados.stream().sorted(Comparator.comparing(Alerta::id).reversed()).limit(limite).toList();
    }
}
