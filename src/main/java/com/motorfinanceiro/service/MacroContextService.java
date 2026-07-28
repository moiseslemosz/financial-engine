package com.motorfinanceiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mantém o contexto macroeconômico corrente em memória, compartilhado
 * entre os agentes de IA (Auditor de FIIs, Analista de Ações).
 *
 * Fonte de atualização:
 * - Selic: valor inicial via application.properties, atualizável manualmente
 *   via endpoint administrativo (ou automaticamente numa fase futura via
 *   API do Banco Central).
 * - Viés do COPOM: atualizado automaticamente toda vez que o
 *   CopomAnalyzerService processa uma nova Ata com sucesso.
 *
 * Por que isso importa: sem contexto macro, a IA analisa VALE3 sem saber
 * que a Selic está em 13,25% ou que o último COPOM foi Hawkish — o que
 * muda completamente a interpretação de um Dividend Yield ou de um
 * comparativo de renda fixa.
 */
@Service
public class MacroContextService {

    private static final Logger log = LoggerFactory.getLogger(MacroContextService.class);

    @Value("${macro.selic.inicial:13.25}")
    private BigDecimal selicAtual;

    private volatile String ultimoViesCopom = "NEUTRO";
    private volatile String ultimoResumoCopom = "Nenhuma análise de COPOM registrada ainda.";
    private volatile LocalDateTime ultimaAtualizacaoCopom = null;

    /**
     * Retorna a Selic atual conhecida pelo sistema.
     */
    public BigDecimal getSelicAtual() {
        return selicAtual;
    }

    /**
     * Atualiza a Selic manualmente (endpoint administrativo).
     */
    public void atualizarSelic(BigDecimal novaSelic) {
        log.info("[MacroContext] Selic atualizada: {}% → {}%", selicAtual, novaSelic);
        this.selicAtual = novaSelic;
    }

    /**
     * Chamado pelo CopomAnalyzerService após cada análise bem-sucedida.
     * Mantém o contexto macro sempre alinhado com a leitura mais recente do COPOM.
     */
    public void atualizarContextoCopom(String vies, String resumo) {
        this.ultimoViesCopom        = vies;
        this.ultimoResumoCopom      = resumo;
        this.ultimaAtualizacaoCopom = LocalDateTime.now();
        log.info("[MacroContext] Viés do COPOM atualizado: {}", vies);
    }

    /**
     * Formata o contexto macro atual como bloco de texto para anexar
     * aos prompts de Auditor de FIIs e Analista de Ações.
     */
    public String formatarParaPrompt() {
        String recencia = ultimaAtualizacaoCopom != null
                ? "atualizado em " + ultimaAtualizacaoCopom.toLocalDate()
                : "sem análise recente";

        return """

                CONTEXTO MACROECONÔMICO ATUAL (considere na análise):
                - Selic vigente: %s%% ao ano
                - Último viés identificado do COPOM: %s (%s)
                - Resumo: %s
                """.formatted(selicAtual, ultimoViesCopom, recencia, ultimoResumoCopom);
    }

    public String getUltimoViesCopom() { return ultimoViesCopom; }
    public LocalDateTime getUltimaAtualizacaoCopom() { return ultimaAtualizacaoCopom; }
}