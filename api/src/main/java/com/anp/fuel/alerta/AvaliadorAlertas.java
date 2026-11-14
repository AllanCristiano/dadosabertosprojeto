package com.anp.fuel.alerta;

import com.anp.fuel.analise.AnaliseDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Núcleo puro (sem I/O) da avaliação: RN06 dispara quando a UF está acima da média E a variação ≥ limite. */
public final class AvaliadorAlertas {
    private AvaliadorAlertas() {}

    public static List<Alerta> avaliar(List<AnaliseDto> ranking, List<RegraAlerta> regras, Instant agora) {
        var disparos = new ArrayList<Alerta>();
        for (var regra : regras) {
            if (!regra.ativa()) continue;
            for (var linha : ranking) {
                if (!regra.uf().equals(linha.uf()) || !regra.produto().equals(linha.produto())) continue;
                if (!linha.acimaDaMedia() || linha.variacaoPct() == null
                        || linha.variacaoPct().compareTo(regra.limitePct()) < 0) continue;
                disparos.add(new Alerta(null, regra.id(), regra.produto(), regra.uf(), linha.mesRef(),
                    linha.variacaoPct(), regra.limitePct(), Severidade.de(linha.variacaoPct(), regra.limitePct()), agora));
            }
        }
        return disparos;
    }
}
