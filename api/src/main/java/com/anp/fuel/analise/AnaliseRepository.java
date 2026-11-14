package com.anp.fuel.analise;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AnaliseRepository {
    private static final String COLS = "mes_ref, uf, produto, preco_medio_uf, preco_medio_nacional, variacao_pct, acima_da_media";
    private static final RowMapper<AnaliseDto> MAP = (rs, i) -> new AnaliseDto(rs.getDate(1).toLocalDate(),
        rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBoolean(7));
    private final JdbcClient jdbc;
    public AnaliseRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<LocalDate> ultimoMes(String produto) {
        return jdbc.sql("SELECT max(mes_ref) FROM dw.vw_preco_vs_media_nacional WHERE produto = ?")
            .param(produto).query((rs, i) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate())
            .optional().map(d -> d);
    }

    public List<AnaliseDto> ranking(String produto, LocalDate mes) {
        return jdbc.sql("SELECT " + COLS + " FROM dw.vw_preco_vs_media_nacional WHERE produto = ? AND mes_ref = ? ORDER BY variacao_pct DESC")
            .params(produto, java.sql.Date.valueOf(mes)).query(MAP).list();
    }

    public List<AnaliseDto> serie(String produto, String uf) {
        return jdbc.sql("SELECT " + COLS + " FROM dw.vw_preco_vs_media_nacional WHERE produto = ? AND uf = ? ORDER BY mes_ref")
            .params(produto, uf).query(MAP).list();
    }

    public List<String> produtos() { return jdbc.sql("SELECT nome FROM dw.dim_produto ORDER BY nome").query(String.class).list(); }
    public List<String> ufs() { return jdbc.sql("SELECT DISTINCT uf FROM dw.dim_localidade ORDER BY uf").query(String.class).list(); }
}
