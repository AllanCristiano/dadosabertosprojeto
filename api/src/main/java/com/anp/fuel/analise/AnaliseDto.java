package com.anp.fuel.analise;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnaliseDto(LocalDate mesRef, String uf, String produto, BigDecimal precoMedioUf,
                         BigDecimal precoMedioNacional, BigDecimal variacaoPct, boolean acimaDaMedia) {}
