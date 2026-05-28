package com.motorfinanceiro.util;
 
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
 
/**
 * Motor determinístico de cálculos financeiros.
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  REGRA ABSOLUTA: nenhum método desta classe jamais deve         ║
 * ║  ser substituído ou chamado pela camada de IA generativa.       ║
 * ║  Todo número aqui é determinístico, auditável e livre           ║
 * ║  de alucinação. A IA traduz — o código calcula.                 ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * Todos os cálculos usam BigDecimal com RoundingMode.HALF_UP,
 * eliminando erros de ponto flutuante (double/float).
 */
public final class Calculations {
 
    /** Escala para valores monetários: R$ 9,95 → 2 casas decimais */
    public static final int SCALE_MONEY = 2;
 
    /** Escala para taxas em cálculos intermediários (alta precisão) */
    public static final int SCALE_RATE = 10;
 
    /** Escala para percentuais exibidos ao usuário: 12,5000% */
    public static final int SCALE_PERCENT = 4;
 
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
 
    /** MathContext com 15 dígitos significativos — suficiente para qualquer cálculo financeiro */
    private static final MathContext MC = new MathContext(15, ROUNDING);
 
    /**
     * Tabela regressiva de IOF sobre rendimentos — Decreto nº 6.306/2007, Art. 7º.
     * Índice 0 = Dia 1 (96%), Índice 29 = Dia 30 (9%).
     * Após o dia 30: alíquota zero.
     */
    private static final int[] IOF_TABLE_PERCENT = {
        96, 93, 90, 87, 84, 81, 78, 75, 72, 69,  // dias 1–10
        66, 63, 60, 57, 54, 51, 48, 45, 42, 39,  // dias 11–20
        36, 33, 30, 27, 24, 21, 18, 15, 12,  9   // dias 21–30
    };
 
    private Calculations() {
        throw new UnsupportedOperationException("Classe utilitária — não instancie.");
    }
 
    // =========================================================================
    // CONVERSÃO DE TAXAS
    // =========================================================================
 
    /**
     * Converte taxa anual percentual para taxa mensal equivalente (regime composto).
     *
     * Fórmula: taxa_mensal = (1 + taxa_anual)^(1/12) − 1
     *
     * Exemplo: 12,5% a.a. → 0,9853...% a.m.
     *
     * @param taxaAnualPercent Taxa anual em percentual (ex: 12.5 para 12,5% a.a.)
     * @return Taxa mensal como decimal com 10 casas de precisão
     */
    public static BigDecimal taxaMensalEquivalente(BigDecimal taxaAnualPercent) {
        BigDecimal taxaAnualDecimal = taxaAnualPercent
                .divide(BigDecimal.valueOf(100), SCALE_RATE, ROUNDING);
 
        // Usa Math.pow para o expoente fracionário (1/12).
        // A conversão double→BigDecimal introduz erro < 10⁻¹⁵, aceitável.
        double taxaMensal = Math.pow(1 + taxaAnualDecimal.doubleValue(), 1.0 / 12) - 1;
        return BigDecimal.valueOf(taxaMensal).setScale(SCALE_RATE, ROUNDING);
    }
 
    // =========================================================================
    // JUROS COMPOSTOS
    // =========================================================================
 
    /**
     * Calcula o montante final com juros compostos, com ou sem aportes mensais.
     *
     * Sem aporte:   FV = PV × (1 + r)^n
     * Com aporte:   FV = PV × (1 + r)^n  +  PMT × [(1 + r)^n − 1] / r
     *
     * Aportes são considerados no final de cada período (anuidade ordinária),
     * convenção padrão do mercado financeiro brasileiro.
     *
     * Caso especial (taxa = 0): retorna PV + PMT × n (sem crescimento).
     *
     * @param valorInicial Principal inicial (PV)
     * @param aporteMensal Aporte mensal (PMT) — use BigDecimal.ZERO se não houver
     * @param taxaMensal   Taxa mensal como decimal (r)
     * @param meses        Número de meses (n)
     * @return Montante final bruto
     */
    public static BigDecimal calcularMontante(
            BigDecimal valorInicial,
            BigDecimal aporteMensal,
            BigDecimal taxaMensal,
            int meses) {
 
        if (meses <= 0) return valorInicial.setScale(SCALE_MONEY, ROUNDING);
 
        // Caso especial: taxa zero
        if (taxaMensal.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal totalAportes = (aporteMensal == null ? BigDecimal.ZERO : aporteMensal)
                    .multiply(BigDecimal.valueOf(meses));
            return valorInicial.add(totalAportes).setScale(SCALE_MONEY, ROUNDING);
        }
 
        // (1 + r)^n
        BigDecimal fator = BigDecimal.ONE.add(taxaMensal).pow(meses, MC);
 
        // PV × (1 + r)^n
        BigDecimal montantePV = valorInicial.multiply(fator, MC);
 
        // Sem aporte
        if (aporteMensal == null || aporteMensal.compareTo(BigDecimal.ZERO) == 0) {
            return montantePV.setScale(SCALE_MONEY, ROUNDING);
        }
 
        // PMT × [(1 + r)^n − 1] / r
        BigDecimal fatorMenosUm = fator.subtract(BigDecimal.ONE);
        BigDecimal montantePMT = aporteMensal
                .multiply(fatorMenosUm, MC)
                .divide(taxaMensal, SCALE_MONEY, ROUNDING);
 
        return montantePV.add(montantePMT).setScale(SCALE_MONEY, ROUNDING);
    }
 
    // =========================================================================
    // TRIBUTAÇÃO — IR (TABELA REGRESSIVA)
    // =========================================================================
 
    /**
     * Retorna a alíquota de IR conforme a tabela regressiva
     * (Lei 11.033/2004, atualizada pela Lei 11.196/2005).
     *
     * Até 180 dias:       22,5%
     * 181 a 360 dias:     20,0%
     * 361 a 720 dias:     17,5%
     * Acima de 720 dias:  15,0%
     *
     * @param diasCorridos Prazo de resgate em dias corridos
     * @return Alíquota como decimal (ex: 0.2250 para 22,5%)
     */
    public static BigDecimal aliquotaIR(int diasCorridos) {
        if (diasCorridos <= 180) return new BigDecimal("0.2250");
        if (diasCorridos <= 360) return new BigDecimal("0.2000");
        if (diasCorridos <= 720) return new BigDecimal("0.1750");
        return new BigDecimal("0.1500");
    }
 
    /**
     * Calcula o valor do IR a pagar sobre o lucro bruto.
     *
     * @param lucroBruto   Rendimento bruto do período
     * @param diasCorridos Prazo em dias corridos
     * @return Valor do IR em R$
     */
    public static BigDecimal calcularIR(BigDecimal lucroBruto, int diasCorridos) {
        if (lucroBruto.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return lucroBruto
                .multiply(aliquotaIR(diasCorridos))
                .setScale(SCALE_MONEY, ROUNDING);
    }
 
    // =========================================================================
    // TRIBUTAÇÃO — IOF (REGRESSIVO)
    // =========================================================================
 
    /**
     * Retorna a alíquota de IOF conforme tabela regressiva
     * (Decreto 6.306/2007, Art. 7º).
     *
     * Incide sobre os rendimentos nos primeiros 30 dias corridos.
     * A partir do dia 31: alíquota zero.
     *
     * @param diasCorridos Prazo de resgate em dias corridos
     * @return Alíquota como decimal (BigDecimal.ZERO se dias > 30)
     */
    public static BigDecimal aliquotaIOF(int diasCorridos) {
        if (diasCorridos <= 0 || diasCorridos > 30) return BigDecimal.ZERO;
        return BigDecimal.valueOf(IOF_TABLE_PERCENT[diasCorridos - 1])
                .divide(BigDecimal.valueOf(100), SCALE_RATE, ROUNDING);
    }
 
    /**
     * Calcula o valor do IOF a pagar sobre o lucro bruto.
     *
     * @param lucroBruto   Rendimento bruto do período
     * @param diasCorridos Prazo em dias corridos
     * @return Valor do IOF em R$ (zero para prazo > 30 dias)
     */
    public static BigDecimal calcularIOF(BigDecimal lucroBruto, int diasCorridos) {
        if (lucroBruto.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return lucroBruto
                .multiply(aliquotaIOF(diasCorridos))
                .setScale(SCALE_MONEY, ROUNDING);
    }
 
    // =========================================================================
    // GANHO REAL (EQUAÇÃO DE FISHER)
    // =========================================================================
 
    /**
     * Calcula o ganho real descontando a inflação do período.
     *
     * Equação de Fisher: Real = [(1 + nominal) / (1 + inflação)] − 1
     *
     * A inflação anual é convertida para o prazo do investimento:
     * inflação_período = (1 + inflação_anual)^(meses/12) − 1
     *
     * Um ganho real negativo indica perda real de poder de compra —
     * o investimento rendeu menos do que a inflação no período.
     *
     * @param rentabilidadeNominalPercent Rentabilidade líquida nominal em %
     *                                   (ex: 10.50 para 10,50%)
     * @param inflacaoAnualPercent        Taxa de inflação anual em %
     *                                   (ex: 4.50 para 4,50%)
     * @param meses                       Prazo do investimento em meses
     * @return Ganho real em percentual, podendo ser negativo
     */
    public static BigDecimal calcularGanhoReal(
            BigDecimal rentabilidadeNominalPercent,
            BigDecimal inflacaoAnualPercent,
            int meses) {
 
        // Converte inflação anual para o prazo do investimento
        BigDecimal inflacaoAnualDecimal = inflacaoAnualPercent
                .divide(BigDecimal.valueOf(100), SCALE_RATE, ROUNDING);
        double inflacaoPeriodo = Math.pow(1 + inflacaoAnualDecimal.doubleValue(), meses / 12.0) - 1;
 
        BigDecimal nominalDecimal = rentabilidadeNominalPercent
                .divide(BigDecimal.valueOf(100), SCALE_RATE, ROUNDING);
 
        // Fisher: (1 + nominal) / (1 + inflação_período) − 1
        BigDecimal numerador   = BigDecimal.ONE.add(nominalDecimal);
        BigDecimal denominador = BigDecimal.ONE.add(BigDecimal.valueOf(inflacaoPeriodo));
 
        return numerador
                .divide(denominador, MC)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE_PERCENT, ROUNDING);
    }
 
    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================
 
    /**
     * Converte meses em dias corridos aproximados (convenção: 1 mês = 30 dias).
     * Padrão adotado pelo mercado financeiro brasileiro para IR e IOF.
     */
    public static int mesesParaDias(int meses) {
        return meses * 30;
    }
 
    /**
     * Calcula rentabilidade percentual do período.
     *
     * rentabilidade = (lucro / totalInvestido) × 100
     *
     * @return BigDecimal.ZERO se totalInvestido for zero
     */
    public static BigDecimal calcularRentabilidade(BigDecimal lucro, BigDecimal totalInvestido) {
        if (totalInvestido.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return lucro
                .divide(totalInvestido, MC)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE_PERCENT, ROUNDING);
    }
}