package com.motorfinanceiro.dto;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
 
/**
 * Resposta enriquecida de um FII: dados numéricos do motor Java
 * + análise completa da camada de IA (estrutura equivalente ao agente).
 */
public record FiiAnaliseResponseDTO(
 
    // ── Dados numéricos (Motor Java — determinístico) ──────────
    String ticker,
    BigDecimal currentPrice,
    BigDecimal dividendYield,
    BigDecimal pvp,
    String source,
    LocalDateTime lastUpdated,
 
    // ── Classificação ───────────────────────────────────────────
    String tipo,
    String segmento,
 
    // ── Avaliação do P/VP e DY ──────────────────────────────────
    String pvpStatus,
    String dyAnalise,
 
    // ── Veredicto qualitativo ────────────────────────────────────
    /** OPORTUNIDADE | NEUTRO | AGUARDAR */
    String veredicto,
    /** APROVADO | EM_OBSERVACAO | REPROVADO */
    String veredictoStatus,
    String veredictoJustificativa,
 
    // ── Análise principal ────────────────────────────────────────
    String analise,
 
    // ── Análise histórica ────────────────────────────────────────
    AnaliseHistoricaDTO analiseHistorica,
 
    // ── Simulador de rendimento ──────────────────────────────────
    SimuladorFiiDTO simulador,
 
    // ── Critérios condicionais por tipo ─────────────────────────
    List<String> criteriosCondicionais,
 
    // ── Matriz de risco ──────────────────────────────────────────
    List<String> pontosFavoraveis,
    List<String> pontosAtencao,
 
    String disclaimer,
    boolean erroAi,
    String mensagemErro
 
) {
    /**
     * Análise histórica do fundo (últimos 3-5 anos).
     * Baseada no conhecimento de treinamento da IA.
     */
    public record AnaliseHistoricaDTO(
        String resistenciaCrises,
        String tendencia3Anos,
        String patrimonioLiquido3Anos,
        String pvpVsMedia
    ) {}
 
    /**
     * Simulador de rendimento com R$ 1.000 investidos.
     * Usa o último dividendo conhecido pela IA (pode estar desatualizado).
     */
    public record SimuladorFiiDTO(
        String dividendoPorCota,
        String frequencia,
        String cotas1000,
        String rendimentoPorPagamento,
        String rendimento12Meses,
        String tendencia
    ) {}
}