package com.anp.fuel.alerta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alertas")
@Validated
@Tag(name = "Alertas", description = "Regras de alerta de preço e alertas disparados (processados via fila)")
public class AlertaController {
    public record CriarRegraRequest(@NotBlank String produto, @NotBlank @Size(min = 2, max = 2) String uf,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal limitePct) {}

    private final RegraService regras;
    private final AlertaRepository alertas;
    private final Mensageria mensageria;

    public AlertaController(RegraService regras, AlertaRepository alertas, Mensageria mensageria) {
        this.regras = regras; this.alertas = alertas; this.mensageria = mensageria;
    }

    @PostMapping("/regras")
    @Operation(summary = "Cria regra: avisar quando a UF passar X% acima da média nacional")
    public ResponseEntity<RegraAlerta> criar(@Valid @RequestBody CriarRegraRequest req) {
        var criada = regras.criar(req.produto(), req.uf(), req.limitePct());
        return ResponseEntity.created(URI.create("/api/v1/alertas/regras/" + criada.id())).body(criada);
    }

    @GetMapping("/regras") public List<RegraAlerta> listarRegras() { return regras.listar(); }
    @GetMapping("/regras/{id}") public RegraAlerta buscarRegra(@PathVariable long id) { return regras.buscar(id); }
    @PostMapping("/regras/{id}/pausar") public RegraAlerta pausar(@PathVariable long id) { return regras.pausar(id); }
    @PostMapping("/regras/{id}/ativar") public RegraAlerta ativar(@PathVariable long id) { return regras.ativar(id); }

    @DeleteMapping("/regras/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        regras.remover(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Alertas disparados, mais recentes primeiro")
    public List<Alerta> recentes(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
        return alertas.recentes(limite);
    }

    @PostMapping("/avaliacoes")
    @Operation(summary = "Enfileira a avaliação das regras (assíncrona); mês opcional")
    public ResponseEntity<Map<String, String>> avaliar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mes) {
        mensageria.publicarCarga(new EventoCargaConcluida(mes));
        return ResponseEntity.accepted().body(Map.of("status", "enfileirado"));
    }
}
