package com.anp.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anp.fuel.alerta.AlertaRepository;
import com.anp.fuel.alerta.RegraRepository;
import com.anp.fuel.analise.AnaliseDto;
import com.anp.fuel.analise.AnaliseRepository;
import com.anp.fuel.preco.PrecoDto;
import com.anp.fuel.preco.PrecoRepository;
import com.anp.fuel.shared.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Testes de contrato HTTP + segurança, sem banco nem broker (repositórios mockados). */
@SpringBootTest(properties = {
    "app.mensageria.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@AutoConfigureMockMvc
class ApiTests {
    @Autowired MockMvc mvc;
    @MockBean PrecoRepository precos;
    @MockBean AnaliseRepository analise;
    @MockBean RegraRepository regraRepo;
    @MockBean AlertaRepository alertaRepo;

    private static String login(String user, String pass) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
    }

    @Test void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/precos")).andExpect(status().isUnauthorized());
    }

    @Test void loginInvalidoRetorna401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("x", "y")))
            .andExpect(status().isUnauthorized());
    }

    @Test void senhaErradaComUsuarioCertoRetorna401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("demo", "errada")))
            .andExpect(status().isUnauthorized());
    }

    @Test void loginComCamposVaziosRetorna400() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("", "")))
            .andExpect(status().isBadRequest());
    }

    @Test void loginValidoDevolveToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("demo", "demo")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test void tokenEmitidoNoLoginAbreOsEndpointsProtegidos() throws Exception {
        var corpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("demo", "demo")))
            .andReturn().getResponse().getContentAsString();
        var token = corpo.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        when(analise.produtos()).thenReturn(List.of("GASOLINA COMUM"));
        mvc.perform(get("/api/v1/analise/produtos").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/analise/produtos").header("Authorization", "Bearer lixo"))
            .andExpect(status().isUnauthorized());
    }

    @Test void healthEhPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test void listaPrecosPaginada() throws Exception {
        var dto = new PrecoDto(LocalDate.of(2025, 1, 2), "GASOLINA COMUM", "SP", "SAO PAULO", "POSTO X", "BR",
            new BigDecimal("6.299"), null);
        when(precos.buscar(any(), anyInt(), anyLong())).thenReturn(List.of(dto));
        when(precos.contar(any())).thenReturn(41L);
        mvc.perform(get("/api/v1/precos?uf=sp&size=20").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(41))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.content[0].uf").value("SP"));
    }

    @Test void tamanhoDePaginaAcimaDoLimiteRetorna4xx() throws Exception {
        mvc.perform(get("/api/v1/precos?size=5000").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test void rankingUsaMesMaisRecente() throws Exception {
        var mes = LocalDate.of(2025, 3, 1);
        when(analise.ultimoMes("GASOLINA COMUM")).thenReturn(Optional.of(mes));
        when(analise.ranking("GASOLINA COMUM", mes)).thenReturn(List.of(new AnaliseDto(mes, "AC", "GASOLINA COMUM",
            new BigDecimal("7.5"), new BigDecimal("6.2"), new BigDecimal("20.97"), true)));
        mvc.perform(get("/api/v1/analise/ranking?produto=gasolina comum").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].acimaDaMedia").value(true));
    }

    @Test void rankingSemDadosRetornaListaVazia() throws Exception {
        when(analise.ultimoMes("ETANOL")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/analise/ranking?produto=etanol").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void rankingComMesExplicitoNormalizaParaDiaUm() throws Exception {
        var mes = LocalDate.of(2025, 3, 1);
        when(analise.ranking("DIESEL", mes)).thenReturn(List.of());
        mvc.perform(get("/api/v1/analise/ranking?produto=diesel&mes=2025-03-17").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void serieProdutosEUfs() throws Exception {
        when(analise.serie("GASOLINA COMUM", "AC")).thenReturn(List.of());
        when(analise.produtos()).thenReturn(List.of("GASOLINA COMUM"));
        when(analise.ufs()).thenReturn(List.of("AC", "SP"));
        mvc.perform(get("/api/v1/analise/serie?produto=gasolina comum&uf=ac").with(jwt())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analise/produtos").with(jwt())).andExpect(jsonPath("$[0]").value("GASOLINA COMUM"));
        mvc.perform(get("/api/v1/analise/ufs").with(jwt())).andExpect(jsonPath("$[1]").value("SP"));
    }

    @Test void paginacaoCalculaTotalDePaginas() {
        assertThat(PageResponse.of(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(PageResponse.of(List.of(1), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThat(PageResponse.of(List.of(1), 0, 20, 21).totalPages()).isEqualTo(2);
    }
}
