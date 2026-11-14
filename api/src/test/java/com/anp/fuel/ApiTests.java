package com.anp.fuel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anp.fuel.analise.AnaliseDto;
import com.anp.fuel.analise.AnaliseRepository;
import com.anp.fuel.preco.PrecoDto;
import com.anp.fuel.preco.PrecoRepository;
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

/** Testes de contrato HTTP + segurança, sem banco (repositórios mockados). */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration"})
@AutoConfigureMockMvc
class ApiTests {
    @Autowired MockMvc mvc;
    @MockBean PrecoRepository precos;
    @MockBean AnaliseRepository analise;

    @Test void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/precos")).andExpect(status().isUnauthorized());
    }

    @Test void loginInvalidoRetorna401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"x\",\"password\":\"y\"}")).andExpect(status().isUnauthorized());
    }

    @Test void loginValidoDevolveToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"demo\",\"password\":\"demo\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
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
}
