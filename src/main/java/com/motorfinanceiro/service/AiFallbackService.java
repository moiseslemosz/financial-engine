package com.motorfinanceiro.service;

import com.motorfinanceiro.exception.AiQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gateway central para chamadas de IA com fallback automático entre modelos.
 *
 * Cadeia configurada no application.properties (ai.models.chain):
 *   gemini-2.5-flash → gemini-1.5-flash → gemini-3.1-flash-lite
 *
 * NOVO: suporte a temperatura por chamada. Chamadas que envolvem
 * cálculo determinístico (ex: Preço Justo de Graham/Bazin) devem usar
 * temperatura 0.0 para garantir que o mesmo input sempre produza o
 * mesmo output — eliminando variação entre consultas do mesmo ticker.
 */
@Service
public class AiFallbackService {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackService.class);

    private final ChatClient chatClient;

    @Value("${ai.models.chain:gemini-2.5-flash,gemini-1.5-flash,gemini-3.1-flash-lite}")
    private String modelsChain;

    /** Temperatura padrão quando a chamada não especifica uma própria */
    @Value("${ai.models.temperature:0.3}")
    private Double defaultTemperature;

    public AiFallbackService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Executa uma chamada de IA com fallback automático e temperatura padrão (0.3).
     * Mantido para compatibilidade com chamadas existentes (COPOM, Auditor de FIIs).
     */
    public String call(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, defaultTemperature);
    }

    /**
     * Executa uma chamada de IA com fallback automático e temperatura explícita.
     *
     * Use temperatura 0.0 para cálculos determinísticos (Graham, Bazin, matemática
     * financeira dentro do texto gerado pela IA) — garante replicabilidade.
     * Use 0.3-0.5 para análises qualitativas onde alguma variação de fraseado é aceitável.
     *
     * @param systemPrompt Instruções do sistema
     * @param userMessage  Mensagem do usuário
     * @param temperature  0.0 (determinístico) a 1.0 (criativo)
     * @return Conteúdo da resposta do modelo
     * @throws AiQuotaExceededException se todos os modelos estiverem indisponíveis
     */
    public String call(String systemPrompt, String userMessage, Double temperature) {
        String[] models = parseModels();
        Exception lastException = null;

        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            boolean isPrimary = (i == 0);

            try {
                if (!isPrimary) {
                    log.warn("[AI Gateway] Usando fallback #{}: {} (temp={})", i, model, temperature);
                } else {
                    log.info("[AI Gateway] Chamando modelo primário: {} (temp={})", model, temperature);
                }

                String response = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .options(GoogleGenAiChatOptions.builder()
                                .model(model)
                                .temperature(temperature)
                                .build())
                        .call()
                        .content();

                if (!isPrimary) {
                    log.info("[AI Gateway] Respondido com sucesso via fallback: {}", model);
                }
                return response;

            } catch (Exception e) {
                if (isFallbackable(e)) {
                    log.warn("[AI Gateway] Modelo {} indisponível ({}). Tentando próximo...",
                            model, extractCause(e));
                    lastException = e;
                } else {
                    log.error("[AI Gateway] Erro não recuperável no modelo {}: {}", model, e.getMessage());
                    throw e;
                }
            }
        }

        log.error("[AI Gateway] Cadeia de fallback esgotada. Modelos tentados: {}. Último erro: {}",
                modelsChain, lastException != null ? lastException.getMessage() : "desconhecido");
        throw new AiQuotaExceededException(
                "Todos os modelos de IA estão temporariamente indisponíveis. Tente novamente em alguns minutos.",
                lastException
        );
    }

    // =========================================================================
    // DETECÇÃO DE ERROS RECUPERÁVEIS (inalterado)
    // =========================================================================

    private boolean isFallbackable(Exception e) {
        String msg  = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String cls  = e.getClass().getName().toLowerCase();
        String cause = e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage().toLowerCase() : "";

        if (msg.contains("429")               || cause.contains("429"))               return true;
        if (msg.contains("resource_exhausted")|| cause.contains("resource_exhausted")) return true;
        if (msg.contains("quota")             || cause.contains("quota"))             return true;
        if (msg.contains("rate limit")        || cause.contains("rate limit"))        return true;
        if (msg.contains("rate_limit")        || cause.contains("rate_limit"))        return true;
        if (msg.contains("too many requests") || cause.contains("too many requests")) return true;
        if (msg.contains("timeout")           || cause.contains("timeout"))           return true;
        if (msg.contains("timed out")         || cause.contains("timed out"))         return true;
        if (msg.contains("503")               || cause.contains("503"))               return true;
        if (msg.contains("unavailable")       || cause.contains("unavailable"))       return true;
        if (cls.contains("timeout")           || cls.contains("sockettimeout"))       return true;
        if (cls.contains("connectexception"))                                          return true;

        return false;
    }

    private String extractCause(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) return e.getMessage();
        if (e.getCause() != null && e.getCause().getMessage() != null) return e.getCause().getMessage();
        return e.getClass().getSimpleName();
    }

    private String[] parseModels() {
        return java.util.Arrays.stream(modelsChain.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }
}