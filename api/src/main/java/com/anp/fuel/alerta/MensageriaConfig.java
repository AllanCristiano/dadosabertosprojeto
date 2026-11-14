package com.anp.fuel.alerta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Topologia: exchange topic {@code anp.eventos} → 2 filas; mensagens rejeitadas vão à DLQ via {@code anp.eventos.dlx}. */
@Configuration
@ConditionalOnProperty(name = "app.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class MensageriaConfig {
    public static final String EXCHANGE = "anp.eventos";
    public static final String DLX = "anp.eventos.dlx";
    public static final String Q_CARGA = "anp.carga.concluida";
    public static final String Q_ALERTA = "anp.alerta.disparado";
    public static final String Q_DLQ = "anp.eventos.dlq";
    public static final String RK_CARGA = "carga.concluida";
    public static final String RK_ALERTA = "alerta.disparado";
    public static final String RK_DLQ = "falha";

    @Bean TopicExchange eventosExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean Queue cargaQueue() { return comDlq(Q_CARGA); }
    @Bean Queue alertaQueue() { return comDlq(Q_ALERTA); }
    @Bean Queue dlq() { return QueueBuilder.durable(Q_DLQ).build(); }

    @Bean Binding cargaBinding() { return BindingBuilder.bind(cargaQueue()).to(eventosExchange()).with(RK_CARGA); }
    @Bean Binding alertaBinding() { return BindingBuilder.bind(alertaQueue()).to(eventosExchange()).with(RK_ALERTA); }
    @Bean Binding dlqBinding() { return BindingBuilder.bind(dlq()).to(deadLetterExchange()).with(RK_DLQ); }

    @Bean MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        var conv = new Jackson2JsonMessageConverter(mapper);
        conv.setAlwaysConvertToInferredType(true);
        return conv;
    }

    private static Queue comDlq(String nome) {
        return QueueBuilder.durable(nome).deadLetterExchange(DLX).deadLetterRoutingKey(RK_DLQ).build();
    }
}
