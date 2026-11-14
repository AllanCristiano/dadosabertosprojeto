package com.anp.fuel.alerta;

import com.anp.fuel.analise.AnaliseRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Orquestra: regras ativas → ranking do mês → disparos novos → persistência → publicação na fila. */
@Service
public class AvaliacaoService {
    private static final Logger log = LoggerFactory.getLogger(AvaliacaoService.class);
    private final RegraRepository regras;
    private final AlertaRepository alertas;
    private final AnaliseRepository analise;
    private final Mensageria mensageria;
    private final Clock clock;

    public AvaliacaoService(RegraRepository regras, AlertaRepository alertas, AnaliseRepository analise,
                            Mensageria mensageria, Clock clock) {
        this.regras = regras; this.alertas = alertas; this.analise = analise;
        this.mensageria = mensageria; this.clock = clock;
    }

    /** @param mesRef mês a avaliar; nulo = mês mais recente disponível de cada produto. @return alertas novos. */
    public int avaliar(LocalDate mesRef) {
        int novos = 0;
        var porProduto = regras.listarAtivas().stream().collect(Collectors.groupingBy(RegraAlerta::produto));
        for (var entrada : porProduto.entrySet()) {
            LocalDate ref = mesRef != null ? mesRef.withDayOfMonth(1) : analise.ultimoMes(entrada.getKey()).orElse(null);
            if (ref == null) {
                log.info("Sem dados para {}; regras ignoradas", entrada.getKey());
                continue;
            }
            var ranking = analise.ranking(entrada.getKey(), ref);
            for (var disparo : AvaliadorAlertas.avaliar(ranking, entrada.getValue(), clock.instant())) {
                var salvo = alertas.registrar(disparo);
                if (salvo.isPresent()) {
                    mensageria.publicarAlerta(salvo.get());
                    novos++;
                }
            }
        }
        log.info("Avaliação concluída: {} alerta(s) novo(s)", novos);
        return novos;
    }
}
