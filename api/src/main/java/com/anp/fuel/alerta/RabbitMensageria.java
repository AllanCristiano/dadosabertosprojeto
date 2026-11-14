package com.anp.fuel.alerta;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMensageria implements Mensageria {
    private final RabbitTemplate template;
    public RabbitMensageria(RabbitTemplate template) { this.template = template; }

    @Override public void publicarCarga(EventoCargaConcluida evento) {
        template.convertAndSend(MensageriaConfig.EXCHANGE, MensageriaConfig.RK_CARGA, evento);
    }

    @Override public void publicarAlerta(Alerta alerta) {
        template.convertAndSend(MensageriaConfig.EXCHANGE, MensageriaConfig.RK_ALERTA, alerta);
    }
}
