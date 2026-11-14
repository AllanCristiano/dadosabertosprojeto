package com.anp.fuel.alerta;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegraRepository implements RegraRepository {
    private static final String COLS = "id, produto, uf, limite_pct, status, criada_em";
    private static final RowMapper<RegraAlerta> MAP = (rs, i) -> new RegraAlerta(rs.getLong(1), rs.getString(2),
        rs.getString(3), rs.getBigDecimal(4), StatusRegra.valueOf(rs.getString(5)), rs.getTimestamp(6).toInstant());
    private final JdbcClient jdbc;
    public JdbcRegraRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public RegraAlerta criar(RegraAlerta r) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO alerta.regra (produto, uf, limite_pct, status, criada_em) VALUES (?, ?, ?, ?, ?)")
            .params(r.produto(), r.uf(), r.limitePct(), r.status().name(), Timestamp.from(r.criadaEm()))
            .update(keys, "id");
        return r.comId(keys.getKey().longValue());
    }

    @Override public Optional<RegraAlerta> buscar(long id) {
        return jdbc.sql("SELECT " + COLS + " FROM alerta.regra WHERE id = ?").param(id).query(MAP).optional();
    }

    @Override public List<RegraAlerta> listar() {
        return jdbc.sql("SELECT " + COLS + " FROM alerta.regra ORDER BY id").query(MAP).list();
    }

    @Override public List<RegraAlerta> listarAtivas() {
        return jdbc.sql("SELECT " + COLS + " FROM alerta.regra WHERE status = 'ATIVA' ORDER BY id").query(MAP).list();
    }

    @Override public boolean existeAtiva(String produto, String uf) {
        return jdbc.sql("SELECT count(*) FROM alerta.regra WHERE status = 'ATIVA' AND produto = ? AND uf = ?")
            .params(produto, uf).query(Long.class).single() > 0;
    }

    @Override public long contarAtivas() {
        return jdbc.sql("SELECT count(*) FROM alerta.regra WHERE status = 'ATIVA'").query(Long.class).single();
    }

    @Override public void atualizarStatus(long id, StatusRegra status) {
        jdbc.sql("UPDATE alerta.regra SET status = ? WHERE id = ?").params(status.name(), id).update();
    }

    @Override public boolean remover(long id) {
        return jdbc.sql("DELETE FROM alerta.regra WHERE id = ?").param(id).update() > 0;
    }
}
