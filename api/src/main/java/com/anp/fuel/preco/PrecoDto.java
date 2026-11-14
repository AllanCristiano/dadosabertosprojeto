package com.anp.fuel.preco;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrecoDto(LocalDate data, String produto, String uf, String municipio, String revenda,
                       String bandeira, BigDecimal precoVenda, BigDecimal precoCompra) {}
