package com.motorfinanceiro.dto;
 
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
 
/**
 * Payload de entrada para o comparativo de renda fixa.
 *
 * Exemplo de uso:
 * POST /api/v1/renda-fixa/comparar
 * {
 *   "valorInicial":  10000.00,
 *   "aporteMensal":    500.00,
 *   "prazoMeses":        24,
 *   "inflacaoAnual":    4.50,
 *   "investimentos": [
 *     { "tipo": "CDB", "taxaAnual": 12.50 },
 *     { "tipo": "LCI", "taxaAnual":  9.00 },
 *     { "tipo": "LCA", "taxaAnual":  9.50 }
 *   ]
 * }
 */
public record RendaFixaRequestDTO(
 
    @NotNull(message = "Valor inicial é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor inicial deve ser maior que zero")
    BigDecimal valorInicial,
 
    @NotNull(message = "Aporte mensal é obrigatório (use 0 se não houver aporte)")
    @DecimalMin(value = "0.00", message = "Aporte mensal não pode ser negativo")
    BigDecimal aporteMensal,
 
    @Min(value = 1,   message = "Prazo mínimo: 1 mês")
    @Max(value = 360, message = "Prazo máximo: 360 meses (30 anos)")
    int prazoMeses,
 
    @NotNull(message = "Inflação anual é obrigatória (use 0 se não quiser calcular ganho real)")
    @DecimalMin(value = "0.00", message = "Inflação não pode ser negativa")
    @DecimalMax(value = "100.00", message = "Inflação parece incorreta (máx 100%)")
    BigDecimal inflacaoAnual,
 
    @NotNull(message = "Informe ao menos um investimento")
    @Size(min = 1, max = 10, message = "Compare entre 1 e 10 investimentos por vez")
    @Valid
    List<InvestimentoItemDTO> investimentos
 
) {}