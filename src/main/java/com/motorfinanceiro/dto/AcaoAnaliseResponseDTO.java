package com.motorfinanceiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dados fundamentalistas de uma ação enriquecidos com análise qualitativa da IA.
 * Os números vêm do motor Java (Fundamentus). A IA só interpreta.
 */
public record AcaoAnaliseResponseDTO(

    // ── Dados fundamentalistas (Fundamentus — determinístico) ──────────
    String ticker,
    BigDecimal cotacao,
    BigDecimal pl,
    BigDecimal pvp,
    BigDecimal dividendYield,
    BigDecimal roe,
    BigDecimal roic,
    BigDecimal margemLiquida,
    BigDecimal margemEbit,
    BigDecimal evEbitda,
    BigDecimal dividaBrutaPatrim,
    BigDecimal crescRec5a,
    BigDecimal liqCorrente,
    String source,
    LocalDateTime lastUpdated,

    // ── Classificação ──────────────────────────────────────────────────
    String setor,
    String segmento,
    String empresa,

    // ── Valuação (calculada pela IA com base nos dados recebidos) ──────
    String precoJustoGraham,
    String precoJustoBazin,
    String margemSeguranca,

    // ── Veredicto ──────────────────────────────────────────────────────
    /** COMPRA | NEUTRO | EVITAR */
    String veredicto,
    /** APROVADO | EM_OBSERVACAO | REPROVADO */
    String veredictoStatus,
    String veredictoJustificativa,

    // ── Análise principal ──────────────────────────────────────────────
    String analiseValuacao,
    String analiseQualidade,
    String analiseRisco,

    // ── Análise histórica ──────────────────────────────────────────────
    AnaliseHistoricaAcaoDTO analiseHistorica,

    // ── Matriz de risco ────────────────────────────────────────────────
    List<String> pontosFavoraveis,
    List<String> pontosAtencao,

    String disclaimer,
    boolean erroAi,
    String mensagemErro

) {
    /**
     * Análise histórica da empresa (resistência a crises, tendência de lucros, dividendos).
     */
    public record AnaliseHistoricaAcaoDTO(
        String resistenciaCrises,
        String tendenciaLucros,
        String politicaDividendos
    ) {}
}