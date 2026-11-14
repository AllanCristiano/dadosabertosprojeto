package com.anp.fuel.preco;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PrecoRepository {
    private static final String FROM = """
        FROM dw.fato_preco f
        JOIN dw.dim_data d ON d.data_id = f.data_id
        JOIN dw.dim_produto p ON p.produto_id = f.produto_id
        JOIN dw.dim_localidade l ON l.localidade_id = f.localidade_id
        JOIN dw.dim_revenda r ON r.revenda_id = f.revenda_id
        LEFT JOIN dw.dim_bandeira b ON b.bandeira_id = r.bandeira_id
        """;
    private final JdbcClient jdbc;
    public PrecoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Filtro(String produto, String uf, String municipio, LocalDate de, LocalDate ate) {}

    private String where(Filtro f, List<Object> args) {
        var sb = new StringBuilder(" WHERE 1=1");
        if (f.produto() != null) { sb.append(" AND p.nome = ?"); args.add(f.produto().toUpperCase()); }
        if (f.uf() != null) { sb.append(" AND l.uf = ?"); args.add(f.uf().toUpperCase()); }
        if (f.municipio() != null) { sb.append(" AND l.municipio = ?"); args.add(f.municipio().toUpperCase()); }
        if (f.de() != null) { sb.append(" AND d.data >= ?"); args.add(java.sql.Date.valueOf(f.de())); }
        if (f.ate() != null) { sb.append(" AND d.data <= ?"); args.add(java.sql.Date.valueOf(f.ate())); }
        return sb.toString();
    }

    public List<PrecoDto> buscar(Filtro f, int limit, long offset) {
        var args = new ArrayList<Object>();
        String w = where(f, args);
        args.add(limit); args.add(offset);
        return jdbc.sql("SELECT d.data, p.nome, l.uf, l.municipio, r.nome, b.nome, f.preco_venda, f.preco_compra "
                + FROM + w + " ORDER BY d.data DESC, f.revenda_id LIMIT ? OFFSET ?")
            .params(args)
            .query((rs, i) -> new PrecoDto(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8)))
            .list();
    }

    public long contar(Filtro f) {
        var args = new ArrayList<Object>();
        String w = where(f, args);
        return jdbc.sql("SELECT count(*) " + FROM + w).params(args).query(Long.class).single();
    }
}
