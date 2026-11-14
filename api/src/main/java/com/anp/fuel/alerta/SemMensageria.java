package com.anp.fuel.alerta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Usada com {@code app.mensageria.enabled=false} (sem broker): só registra em log. */
@Component
@ConditionalOnProperty(name = "app.mensageria.enabled", havingValue = "false")
public class SemMensageria implements Mensageria {
    private static final Logger log = LoggerFactory.getLogger(SemMensageria.class);

    @Override public void publicarCarga(EventoCargaConcluida evento) { log.warn("Mensageria desligada; evento ignorado: {}", evento); }
    @Override public void publicarAlerta(Alerta alerta) { log.warn("Mensageria desligada; alerta ignorado: {}", alerta); }
}
