package com.anp.fuel.alerta;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAlertaRepository implements AlertaRepository {
    private static final RowMapper<Alerta> MAP = (rs, i) -> new Alerta(rs.getLong(1), rs.getLong(2), rs.getString(3),
        rs.getString(4), rs.getDate(5).toLocalDate(), rs.getBigDecimal(6), rs.getBigDecimal(7),
        Severidade.valueOf(rs.getString(8)), rs.getTimestamp(9).toInstant());
    private final JdbcClient jdbc;
    public JdbcAlertaRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Alerta> registrar(Alerta a) {
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.sql("INSERT INTO alerta.alerta_disparado (regra_id, produto, uf, mes_ref, variacao_pct, limite_pct, severidade, criado_em) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(a.regraId(), a.produto(), a.uf(), java.sql.Date.valueOf(a.mesRef()), a.variacaoPct(),
                    a.limitePct(), a.severidade().name(), Timestamp.from(a.criadoEm()))
                .update(keys, "id");
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
        return Optional.of(a.comId(keys.getKey().longValue()));
    }

    @Override public List<Alerta> recentes(int limite) {
        return jdbc.sql("SELECT id, regra_id, produto, uf, mes_ref, variacao_pct, limite_pct, severidade, criado_em "
                + "FROM alerta.alerta_disparado ORDER BY criado_em DESC, id DESC LIMIT ?")
            .param(limite).query(MAP).list();
    }
}
