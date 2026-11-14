package com.anp.fuel.alerta;

import java.time.LocalDate;

/** Mensagem publicada quando o ETL termina. {@code mesRef} nulo = avaliar o mês mais recente de cada produto. */
public record EventoCargaConcluida(LocalDate mesRef) {}
