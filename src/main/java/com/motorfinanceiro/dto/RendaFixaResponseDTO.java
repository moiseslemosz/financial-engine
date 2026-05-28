package com.motorfinanceiro.dto;
 
import java.math.BigDecimal;
import java.util.List;
 
/**
 * Resposta completa do comparativo de renda fixa.
 * O campo `comparativo` está ordenado do melhor para o pior montante líquido.
 */
public record RendaFixaResponseDTO(
 
    /** Valor aplicado inicialmente */
    BigDecimal valorInicial,
 
    /** Aporte mensal informado */
    BigDecimal aporteMensal,
 
    /** Prazo do investimento em meses */
    int prazoMeses,
 
    /** Total de capital desembolsado: valorInicial + (aporteMensal × prazoMeses) */
    BigDecimal totalInvestido,
 
    /** Comparativo ordenado do maior para o menor montante líquido */
    List<ResultadoCalculoDTO> comparativo
 
) {}