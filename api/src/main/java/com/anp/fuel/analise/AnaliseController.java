package com.anp.fuel.analise;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analise")
@Tag(name = "Análise", description = "Responde: onde o preço subiu acima da média nacional?")
public class AnaliseController {
    private final AnaliseRepository repo;
    public AnaliseController(AnaliseRepository repo) { this.repo = repo; }

    @GetMapping("/ranking")
    @Operation(summary = "Ranking de UFs por variação vs. média nacional em um mês (padrão: mês mais recente)")
    public List<AnaliseDto> ranking(@RequestParam String produto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mes) {
        String p = produto.toUpperCase();
        LocalDate ref = mes != null ? mes.withDayOfMonth(1) : repo.ultimoMes(p).orElse(null);
        return ref == null ? List.of() : repo.ranking(p, ref);
    }

    @GetMapping("/serie")
    @Operation(summary = "Série mensal de uma UF contra a média nacional")
    public List<AnaliseDto> serie(@RequestParam String produto, @RequestParam String uf) {
        return repo.serie(produto.toUpperCase(), uf.toUpperCase());
    }

    @GetMapping("/produtos") public List<String> produtos() { return repo.produtos(); }
    @GetMapping("/ufs") public List<String> ufs() { return repo.ufs(); }
}
