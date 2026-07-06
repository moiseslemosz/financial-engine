package com.motorfinanceiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dados fundamentalistas de uma ação da B3.
 * Todos os campos são imutáveis (Java Record).
 */
public record AcaoResponseDTO(
    String ticker,

    /** Cotação atual (R$) */
    BigDecimal cotacao,

    /** P/L — Preço / Lucro por Ação */
    BigDecimal pl,

    /** P/VP — Preço / Valor Patrimonial por Ação */
    BigDecimal pvp,

    /** Dividend Yield (% ao ano) */
    BigDecimal dividendYield,

    /** ROE — Retorno sobre Patrimônio Líquido (%) */
    BigDecimal roe,

    /** ROIC — Retorno sobre Capital Investido (%) */
    BigDecimal roic,

    /** Margem Líquida (%) */
    BigDecimal margemLiquida,

    /** Margem EBIT (%) */
    BigDecimal margemEbit,

    /** EV/EBITDA */
    BigDecimal evEbitda,

    /** Dívida Bruta / Patrimônio Líquido */
    BigDecimal dividaBrutaPatrim,

    /** Crescimento de Receita nos últimos 5 anos (%) */
    BigDecimal crescRec5a,

    /** Liquidez Corrente */
    BigDecimal liqCorrente,

    String source,
    LocalDateTime lastUpdated
) {
    public AcaoResponseDTO {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker não pode ser nulo ou vazio.");
        }
    }
}