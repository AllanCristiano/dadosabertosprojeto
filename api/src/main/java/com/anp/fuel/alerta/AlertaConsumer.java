package com.anp.fuel.alerta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Ponto de extensão de notificação (e-mail, Slack…): hoje registra em log. */
@Component
@ConditionalOnProperty(name = "app.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class AlertaConsumer {
    private static final Logger log = LoggerFactory.getLogger(AlertaConsumer.class);

    @RabbitListener(queues = MensageriaConfig.Q_ALERTA)
    public void notificar(Alerta alerta) {
        log.info("ALERTA {} — {} em {} está {}% acima da média (limite {}%) em {}", alerta.severidade(),
            alerta.produto(), alerta.uf(), alerta.variacaoPct(), alerta.limitePct(), alerta.mesRef());
    }
}
