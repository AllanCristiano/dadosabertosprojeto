package com.anp.fuel.preco;

import com.anp.fuel.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/precos")
@Validated
@Tag(name = "Preços", description = "Coletas de preço individuais (fato), paginadas e filtráveis")
public class PrecoController {
    private final PrecoRepository repo;
    public PrecoController(PrecoRepository repo) { this.repo = repo; }

    @GetMapping
    @Operation(summary = "Lista coletas de preço com filtros e paginação")
    public PageResponse<PrecoDto> listar(
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        var f = new PrecoRepository.Filtro(produto, uf, municipio, de, ate);
        return PageResponse.of(repo.buscar(f, size, (long) page * size), page, size, repo.contar(f));
    }
}
