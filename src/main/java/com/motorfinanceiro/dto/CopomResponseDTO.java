package com.motorfinanceiro.dto;
 
import java.util.List;
 
/**
 * Resposta da análise semântica do comunicado do COPOM.
 *
 * Produzida pela camada cognitiva (Gemini) a partir do texto da Ata.
 * Nenhum valor numérico financeiro é gerado aqui — apenas interpretação.
 */
public record CopomResponseDTO(
 
    /**
     * Viés da política monetária identificado.
     * HAWKISH = tendência de alta de juros
     * DOVISH  = tendência de corte de juros
     * NEUTRO  = sem direcionamento claro
     */
    String vies,
 
    /** Título curto e direto para o investidor */
    String titulo,
 
    /** Resumo executivo em linguagem acessível */
    String resumo,
 
    /** Impacto prático em CDB, Tesouro Direto, LCI, LCA */
    String impactoRendaFixa,
 
    /** Impacto prático nos fundos imobiliários */
    String impactoFiis,
 
    /** Frases do texto original que sinalizam o viés identificado */
    List<String> frasesChave,
 
    /** O que esperar nas próximas reuniões do COPOM */
    String perspectiva,
 
    /** Primeiros 200 caracteres do texto analisado (referência) */
    String textoAnalisadoTrecho,
 
    /** true se o modelo retornou erro ou texto não reconhecido */
    boolean erroAi,
 
    /** Mensagem de erro (preenchida apenas se erroAi = true) */
    String mensagemErro
 
) {}