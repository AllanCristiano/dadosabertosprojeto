# Relatório Power BI

## Conexão (PostgreSQL)
- Servidor: `localhost:5433` · Banco: `anp` · Usuário/Senha: `anp` / `anp`
- Modo: **Import**. Tabelas: `dw.vw_preco_vs_media_nacional`, `dw.dim_produto`, `dw.dim_localidade`.
  (o `dw.mv_preco_mensal_uf` já traz o agregado mensal; a fato tem 1M+ linhas, evite importá-la inteira.)

## Medidas DAX
```dax
Preço Médio UF       = AVERAGE ( vw_preco_vs_media_nacional[preco_medio_uf] )
Preço Médio Nacional = AVERAGE ( vw_preco_vs_media_nacional[preco_medio_nacional] )
Variação % vs Brasil = AVERAGE ( vw_preco_vs_media_nacional[variacao_pct] ) / 100
UFs Acima da Média   = CALCULATE ( DISTINCTCOUNT ( vw_preco_vs_media_nacional[uf] ),
                                   vw_preco_vs_media_nacional[acima_da_media] = TRUE() )
```

## Páginas sugeridas
1. **Onde subiu acima da média?** Mapa do Brasil (UF × `Variação % vs Brasil`, escala divergente verde→vermelho), segmentação por produto e mês, cartões com `UFs Acima da Média` e maior alta.
2. **Evolução:** linhas `Preço Médio UF` × `Preço Médio Nacional` por `mes_ref`, com segmentação de UF.

Salve o arquivo como `powerbi/pulso-combustiveis.pbix` e exporte capturas para `docs/`.
