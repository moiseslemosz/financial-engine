package com.motorfinanceiro.dto;
 
import com.motorfinanceiro.model.TipoInvestimento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
 
/**
 * Representa um único investimento a ser calculado dentro do comparativo.
 */
public record InvestimentoItemDTO(
 
    @NotNull(message = "Tipo de investimento é obrigatório")
    TipoInvestimento tipo,
 
    @NotNull(message = "Taxa anual é obrigatória")
    @DecimalMin(value = "0.01", message = "Taxa anual deve ser maior que zero")
    @DecimalMax(value = "100.00", message = "Taxa anual parece incorreta (máx 100%)")
    BigDecimal taxaAnual
 
) {}