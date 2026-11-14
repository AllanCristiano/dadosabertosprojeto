package com.anp.fuel.alerta;

import static com.anp.fuel.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class MensageriaTest {
    private final MensageriaConfig config = new MensageriaConfig();

    @Test void rabbitMensageriaPublicaNoExchangeComChaveCerta() {
        var template = mock(RabbitTemplate.class);
        var m = new RabbitMensageria(template);
        var evento = new EventoCargaConcluida(MES);
        var alerta = alerta(1L, MES);
        m.publicarCarga(evento);
        m.publicarAlerta(alerta);
        verify(template).convertAndSend("anp.eventos", "carga.concluida", evento);
        verify(template).convertAndSend("anp.eventos", "alerta.disparado", alerta);
    }

    @Test void semMensageriaApenasRegistraEmLog() {
        var m = new SemMensageria();
        assertThatCode(() -> {
            m.publicarCarga(new EventoCargaConcluida(null));
            m.publicarAlerta(alerta(1L, MES));
        }).doesNotThrowAnyException();
    }

    @Test void topologiaFilasComDeadLetter() {
        assertThat(config.eventosExchange().getName()).isEqualTo("anp.eventos");
        assertThat(config.deadLetterExchange().getName()).isEqualTo("anp.eventos.dlx");
        for (Queue fila : new Queue[] {config.cargaQueue(), config.alertaQueue()}) {
            assertThat(fila.isDurable()).isTrue();
            assertThat(fila.getArguments()).containsEntry("x-dead-letter-exchange", "anp.eventos.dlx")
                .containsEntry("x-dead-letter-routing-key", "falha");
        }
        assertThat(config.dlq().getName()).isEqualTo("anp.eventos.dlq");
        assertThat(config.dlq().getArguments()).doesNotContainKey("x-dead-letter-exchange");
    }

    @Test void bindingsLigamExchangeAsFilas() {
        assertBinding(config.cargaBinding(), "anp.carga.concluida", "carga.concluida");
        assertBinding(config.alertaBinding(), "anp.alerta.disparado", "alerta.disparado");
        assertBinding(config.dlqBinding(), "anp.eventos.dlq", "falha");
    }

    private static void assertBinding(Binding b, String fila, String chave) {
        assertThat(b.getDestination()).isEqualTo(fila);
        assertThat(b.getRoutingKey()).isEqualTo(chave);
    }

    @Test void conversorJsonViajaDatasEInfereTipoDoListener() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var conv = (Jackson2JsonMessageConverter) config.jsonMessageConverter(mapper);
        var msg = conv.toMessage(new EventoCargaConcluida(MES), new MessageProperties());
        assertThat(new String(msg.getBody())).contains("2026-06-01");
        msg.getMessageProperties().setInferredArgumentType(EventoCargaConcluida.class);
        assertThat(conv.fromMessage(msg)).isEqualTo(new EventoCargaConcluida(MES));
    }

    @Test void conversorAceitaPayloadDoAirflowSemData() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var conv = (Jackson2JsonMessageConverter) config.jsonMessageConverter(mapper);
        var props = new MessageProperties();
        props.setContentType("application/json");
        props.setInferredArgumentType(EventoCargaConcluida.class);
        var msg = new org.springframework.amqp.core.Message("{}".getBytes(), props);
        assertThat(conv.fromMessage(msg)).isEqualTo(new EventoCargaConcluida(null));
    }

    @Test void consumidoresDelegamParaOServico() {
        var avaliacao = mock(AvaliacaoService.class);
        new CargaConsumer(avaliacao).aoConcluirCarga(new EventoCargaConcluida(MES));
        verify(avaliacao).avaliar(MES);
        assertThatCode(() -> new AlertaConsumer().notificar(alerta(1L, MES))).doesNotThrowAnyException();
    }

    @Test void relogioEhUtc() {
        assertThat(new RelogioConfig().clock().getZone().getId()).isEqualTo("Z");
    }
}
