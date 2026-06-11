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
 *   gemini-2.5-flash (20 RPD) → gemini-1.5-flash (1.5K RPD) → gemini-3.1-flash-lite (500 RPD)
 *
 * Comportamento:
 * - Tenta o modelo primário normalmente.
 * - Se receber erro de cota (429 / RESOURCE_EXHAUSTED) ou timeout,
 *   registra warning e tenta o próximo modelo da cadeia.
 * - Se todos os modelos falharem por cota/timeout, lança AiQuotaExceededException.
 * - Erros não relacionados a cota (ex: prompt inválido) propagam imediatamente
 *   sem tentar o fallback.
 */
@Service
public class AiFallbackService {
 
    private static final Logger log = LoggerFactory.getLogger(AiFallbackService.class);
 
    private final ChatClient chatClient;
 
    /**
     * Cadeia de modelos separada por vírgula, tentada na ordem definida.
     * Exemplo: gemini-2.5-flash,gemini-1.5-flash,gemini-3.1-flash-lite
     */
    @Value("${ai.models.chain:gemini-2.5-flash,gemini-1.5-flash,gemini-3.1-flash-lite}")
    private String modelsChain;
 
    /** Temperatura padrão para todas as chamadas (baixa = respostas mais consistentes) */
    @Value("${ai.models.temperature:0.3}")
    private Double temperature;
 
    public AiFallbackService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
 
    /**
     * Executa uma chamada de IA com fallback automático.
     *
     * @param systemPrompt Instruções do sistema (system prompt)
     * @param userMessage  Mensagem do usuário
     * @return Conteúdo da resposta do modelo
     * @throws AiQuotaExceededException se todos os modelos estiverem indisponíveis
     */
    public String call(String systemPrompt, String userMessage) {
        String[] models = parseModels();
        Exception lastException = null;
 
        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            boolean isPrimary = (i == 0);
 
            try {
                if (!isPrimary) {
                    log.warn("[AI Gateway] Usando fallback #{}: {}", i, model);
                } else {
                    log.info("[AI Gateway] Chamando modelo primário: {}", model);
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
                    // continua para o próximo modelo
                } else {
                    // Erros não relacionados a cota/timeout propagam imediatamente
                    log.error("[AI Gateway] Erro não recuperável no modelo {}: {}", model, e.getMessage());
                    throw e;
                }
            }
        }
 
        // Todos os modelos falharam
        log.error("[AI Gateway] Cadeia de fallback esgotada. Modelos tentados: {}. Último erro: {}",
                modelsChain, lastException != null ? lastException.getMessage() : "desconhecido");
        throw new AiQuotaExceededException(
                "Todos os modelos de IA estão temporariamente indisponíveis. Tente novamente em alguns minutos.",
                lastException
        );
    }
 
    // =========================================================================
    // DETECÇÃO DE ERROS RECUPERÁVEIS
    // =========================================================================
 
    /**
     * Determina se o erro justifica tentar o próximo modelo da cadeia.
     *
     * Erros recuperáveis (fallback permitido):
     * - 429 Too Many Requests (cota esgotada)
     * - RESOURCE_EXHAUSTED (Google AI)
     * - Timeout de conexão/leitura
     * - Serviço temporariamente indisponível (503)
     *
     * Erros NÃO recuperáveis (propagam imediatamente):
     * - 400 Bad Request (prompt inválido)
     * - 401/403 Unauthorized (API key inválida)
     * - Erros de parse/lógica da aplicação
     */
    private boolean isFallbackable(Exception e) {
        String msg  = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String cls  = e.getClass().getName().toLowerCase();
        String cause = e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage().toLowerCase() : "";
 
        // Cota/rate limit
        if (msg.contains("429")              || cause.contains("429"))              return true;
        if (msg.contains("resource_exhausted")|| cause.contains("resource_exhausted")) return true;
        if (msg.contains("quota")            || cause.contains("quota"))            return true;
        if (msg.contains("rate limit")       || cause.contains("rate limit"))       return true;
        if (msg.contains("rate_limit")       || cause.contains("rate_limit"))       return true;
        if (msg.contains("too many requests")|| cause.contains("too many requests")) return true;
 
        // Timeout / indisponibilidade
        if (msg.contains("timeout")          || cause.contains("timeout"))         return true;
        if (msg.contains("timed out")        || cause.contains("timed out"))        return true;
        if (msg.contains("503")              || cause.contains("503"))              return true;
        if (msg.contains("unavailable")      || cause.contains("unavailable"))      return true;
        if (cls.contains("timeout")          || cls.contains("sockettimeout"))      return true;
        if (cls.contains("connectexception"))                                        return true;
 
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