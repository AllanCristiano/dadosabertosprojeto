package com.anp.fuel;

import static com.anp.fuel.support.Fixtures.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anp.fuel.alerta.AlertaException;
import com.anp.fuel.alerta.AlertaRepository;
import com.anp.fuel.alerta.EventoCargaConcluida;
import com.anp.fuel.alerta.Mensageria;
import com.anp.fuel.alerta.RegraRepository;
import com.anp.fuel.alerta.RegraService;
import com.anp.fuel.alerta.StatusRegra;
import com.anp.fuel.analise.AnaliseRepository;
import com.anp.fuel.preco.PrecoRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP do módulo de alertas: status codes, validação, segurança e CORS. */
@SpringBootTest(properties = {
    "app.mensageria.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@AutoConfigureMockMvc
class AlertaApiTest {
    @Autowired MockMvc mvc;
    @MockBean RegraService regras;
    @MockBean AlertaRepository alertas;
    @MockBean Mensageria mensageria;
    @MockBean RegraRepository regraRepo;
    @MockBean PrecoRepository precos;
    @MockBean AnaliseRepository analise;

    private static final String BODY = "{\"produto\":\"gasolina comum\",\"uf\":\"ac\",\"limitePct\":5.5}";

    @Test void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/alertas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/alertas/regras").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test void criaRegraRetorna201ComLocation() throws Exception {
        when(regras.criar("gasolina comum", "ac", bd("5.5")))
            .thenReturn(regra(7L, GASOLINA, "AC", "5.5", StatusRegra.ATIVA));
        mvc.perform(post("/api/v1/alertas/regras").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/alertas/regras/7"))
            .andExpect(jsonPath("$.status").value("ATIVA"))
            .andExpect(jsonPath("$.uf").value("AC"));
    }

    @Test void corpoInvalidoRetorna400() throws Exception {
        for (var corpo : List.of(
                "{\"produto\":\"\",\"uf\":\"AC\",\"limitePct\":5}",
                "{\"produto\":\"X\",\"uf\":\"ACRE\",\"limitePct\":5}",
                "{\"produto\":\"X\",\"uf\":\"AC\"}",
                "{\"produto\":\"X\",\"uf\":\"AC\",\"limitePct\":0}",
                "{\"produto\":\"X\",\"uf\":\"AC\",\"limitePct\":101}")) {
            mvc.perform(post("/api/v1/alertas/regras").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
        }
    }

    @Test void regraDuplicadaRetorna409() throws Exception {
        when(regras.criar(any(), any(), any())).thenThrow(new AlertaException.Duplicada(GASOLINA, "AC"));
        mvc.perform(post("/api/v1/alertas/regras").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Já existe regra ativa para GASOLINA COMUM em AC"));
    }

    @Test void regraInvalidaDeNegocioRetorna422() throws Exception {
        when(regras.criar(any(), any(), any())).thenThrow(new AlertaException.Invalida("UF inválida: ac"));
        mvc.perform(post("/api/v1/alertas/regras").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("UF inválida: ac"));
    }

    @Test void listaEBuscaRegras() throws Exception {
        var r = regra(1L, GASOLINA, "AC", "5", StatusRegra.ATIVA);
        when(regras.listar()).thenReturn(List.of(r));
        when(regras.buscar(1)).thenReturn(r);
        when(regras.buscar(2)).thenThrow(new AlertaException.NaoEncontrada(2));
        mvc.perform(get("/api/v1/alertas/regras").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/v1/alertas/regras/1").with(jwt())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alertas/regras/2").with(jwt()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Regra 2 não encontrada"));
    }

    @Test void pausarEAtivar() throws Exception {
        when(regras.pausar(1)).thenReturn(regra(1L, GASOLINA, "AC", "5", StatusRegra.PAUSADA));
        when(regras.ativar(1)).thenReturn(regra(1L, GASOLINA, "AC", "5", StatusRegra.ATIVA));
        mvc.perform(post("/api/v1/alertas/regras/1/pausar").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSADA"));
        mvc.perform(post("/api/v1/alertas/regras/1/ativar").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test void removerRetorna204Ou404() throws Exception {
        mvc.perform(delete("/api/v1/alertas/regras/1").with(jwt())).andExpect(status().isNoContent());
        verify(regras).remover(1);
        doThrow(new AlertaException.NaoEncontrada(9)).when(regras).remover(9);
        mvc.perform(delete("/api/v1/alertas/regras/9").with(jwt())).andExpect(status().isNotFound());
    }

    @Test void listaAlertasDisparadosComLimite() throws Exception {
        when(alertas.recentes(anyInt())).thenReturn(List.of(alerta(1L, MES).comId(5L)));
        mvc.perform(get("/api/v1/alertas").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].severidade").value("CRITICA"))
            .andExpect(jsonPath("$[0].mesRef").value("2026-06-01"));
        verify(alertas).recentes(50);
        mvc.perform(get("/api/v1/alertas?limite=0").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/alertas?limite=201").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test void avaliarEnfileiraEvento() throws Exception {
        mvc.perform(post("/api/v1/alertas/avaliacoes").with(jwt()))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("enfileirado"));
        verify(mensageria).publicarCarga(new EventoCargaConcluida(null));
        mvc.perform(post("/api/v1/alertas/avaliacoes?mes=2026-06-01").with(jwt())).andExpect(status().isAccepted());
        verify(mensageria).publicarCarga(new EventoCargaConcluida(LocalDate.of(2026, 6, 1)));
    }

    @Test void corsLiberaDeleteParaOrigemConfigurada() throws Exception {
        mvc.perform(options("/api/v1/alertas/regras/1").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "DELETE"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/v1/alertas/regras/1").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "DELETE"))
            .andExpect(status().isForbidden());
    }
}
