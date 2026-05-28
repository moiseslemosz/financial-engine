package com.motorfinanceiro.dto;
 
import java.math.BigDecimal;
 
/**
 * Resultado completo do cálculo para um único investimento.
 * A lista de resultados é ordenada do maior para o menor montante líquido.
 */
public record ResultadoCalculoDTO(
 
    /** Nome do enum (ex: "CDB", "LCI") */
    String tipo,
 
    /** Descrição completa (ex: "Certificado de Depósito Bancário") */
    String descricao,
 
    /** Taxa anual informada no request (ex: 12.50) */
    BigDecimal taxaAnual,
 
    /** Se o investimento é isento de IR para pessoa física */
    boolean isento,
 
    /** Alíquota de IR aplicada, em % (0 se isento — ex: 17.5) */
    BigDecimal aliquotaIR,
 
    /** Montante final antes dos impostos */
    BigDecimal montanteBruto,
 
    /** Montante final após IR e IOF */
    BigDecimal montanteLiquido,
 
    /** Rendimento bruto = montanteBruto - totalInvestido */
    BigDecimal lucroBruto,
 
    /** Rendimento líquido = lucroBruto - IR - IOF */
    BigDecimal lucroLiquido,
 
    /** Valor do IR pago (R$) */
    BigDecimal impostoIR,
 
    /** Valor do IOF pago (R$) — zero para prazo > 30 dias */
    BigDecimal impostoIOF,
 
    /** Rentabilidade bruta do período em % */
    BigDecimal rentabilidadeBruta,
 
    /** Rentabilidade líquida do período em % */
    BigDecimal rentabilidadeLiquida,
 
    /**
     * Ganho real em % — rentabilidade líquida descontada a inflação do período.
     * Negativo significa perda real de poder de compra.
     */
    BigDecimal ganhoReal
 
) {}