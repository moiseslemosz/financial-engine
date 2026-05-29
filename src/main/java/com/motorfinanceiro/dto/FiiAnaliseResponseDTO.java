package com.motorfinanceiro.dto;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
 
/**
 * Resposta enriquecida de um FII: dados numéricos do motor Java
 * combinados com a interpretação qualitativa da camada de IA.
 *
 * Princípio: os números vêm do código, a linguagem natural vem da IA.
 */
public record FiiAnaliseResponseDTO(
 
    // ── Dados numéricos (Motor Java — determinístico) ──────────────
    String ticker,
    BigDecimal currentPrice,
    BigDecimal dividendYield,
    BigDecimal pvp,
    String source,
    LocalDateTime lastUpdated,
 
    // ── Análise qualitativa (Camada de IA — Gemini) ────────────────
 
    /**
     * Veredito resumido.
     * OPORTUNIDADE = dados sugerem ponto interessante de entrada
     * NEUTRO       = nem atrativo nem repulsivo no momento
     * AGUARDAR     = sinais de cautela presentes
     */
    String veredicto,
 
    /** Análise contextualizada conectando preço, DY e P/VP */
    String analise,
 
    /** Aspectos favoráveis identificados nos dados */
    List<String> pontosFavoraveis,
 
    /** Pontos que merecem atenção antes de qualquer decisão */
    List<String> pontosAtencao,
 
    /** Aviso legal obrigatório */
    String disclaimer,
 
    /** true se a análise de IA falhou (dados numéricos ainda retornados) */
    boolean erroAi,
 
    /** Motivo do erro de IA, se houver */
    String mensagemErro
 
) {}