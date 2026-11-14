package com.anp.fuel.alerta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consome "carga concluída" e avalia as regras. Falhas repetidas → DLQ (retry configurado em application.yml). */
@Component
@ConditionalOnProperty(name = "app.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class CargaConsumer {
    private static final Logger log = LoggerFactory.getLogger(CargaConsumer.class);
    private final AvaliacaoService avaliacao;
    public CargaConsumer(AvaliacaoService avaliacao) { this.avaliacao = avaliacao; }

    @RabbitListener(queues = MensageriaConfig.Q_CARGA)
    public void aoConcluirCarga(EventoCargaConcluida evento) {
        log.info("Carga concluída recebida: {}", evento);
        avaliacao.avaliar(evento.mesRef());
    }
}
