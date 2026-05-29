package com.motorfinanceiro.service;
 
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.CopomResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
 
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
 
/**
 * Agente: Tradutor do COPOM.
 *
 * Recebe o texto de uma Ata ou comunicado do COPOM e usa o Gemini
 * para retornar uma análise estruturada em linguagem acessível.
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  Este service NUNCA executa cálculos financeiros.               ║
 * ║  Ele apenas interpreta linguagem — o motor Java calcula.        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Service
public class CopomAnalyzerService {
 
    private static final Logger log = LoggerFactory.getLogger(CopomAnalyzerService.class);
 
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
 
    @Value("classpath:prompts/copom-translator.st")
    private Resource systemPromptResource;
 
    public CopomAnalyzerService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient   = chatClient;
        this.objectMapper = objectMapper;
    }
 
    /**
     * Analisa semanticamente um comunicado do COPOM.
     *
     * Fluxo:
     * 1. Carrega o system prompt do arquivo de recurso
     * 2. Envia o texto ao Gemini via Spring AI
     * 3. Parseia o JSON retornado pelo modelo
     * 4. Retorna CopomResponseDTO estruturado
     *
     * Em caso de falha da IA, retorna um DTO com erroAi=true
     * e a mensagem de erro, sem lançar exceção para o controller.
     *
     * @param textoAta Texto da Ata ou comunicado do COPOM
     * @return Análise estruturada com viés, impactos e frases-chave
     */
    public CopomResponseDTO analisar(String textoAta) {
        log.info("[COPOM] Iniciando análise semântica. Tamanho do texto: {} caracteres",
                textoAta.length());
 
        String trechoReferencia = textoAta.substring(0, Math.min(200, textoAta.length())) + "...";
 
        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
 
            String jsonBruto = chatClient.prompt()
                    .system(systemPrompt)
                    .user(textoAta)
                    .call()
                    .content();
 
            log.debug("[COPOM] Resposta bruta da IA: {}", jsonBruto);
 
            return parsearResposta(jsonBruto, trechoReferencia);
 
        } catch (Exception e) {
            log.error("[COPOM] Falha ao chamar a IA: {}", e.getMessage());
            return respostaDeErro(trechoReferencia, "Serviço de IA indisponível: " + e.getMessage());
        }
    }
 
    // =========================================================================
    // PARSING DA RESPOSTA
    // =========================================================================
 
    private CopomResponseDTO parsearResposta(String jsonBruto, String trechoReferencia) {
        try {
            String jsonLimpo = limparMarkdown(jsonBruto);
            JsonNode root    = objectMapper.readTree(jsonLimpo);
 
            // Verifica se o modelo retornou um erro explícito
            if (root.has("erro")) {
                return respostaDeErro(trechoReferencia, root.get("erro").asText());
            }
 
            return new CopomResponseDTO(
                    textOr(root, "vies", "NEUTRO"),
                    textOr(root, "titulo", "Análise indisponível"),
                    textOr(root, "resumo", ""),
                    textOr(root, "impactoRendaFixa", ""),
                    textOr(root, "impactoFiis", ""),
                    parsearLista(root, "frasesChave"),
                    textOr(root, "perspectiva", ""),
                    trechoReferencia,
                    false,
                    null
            );
 
        } catch (Exception e) {
            log.error("[COPOM] Falha ao parsear JSON da IA: {}", e.getMessage());
            log.debug("[COPOM] JSON que falhou no parse: {}", jsonBruto);
            return respostaDeErro(trechoReferencia,
                    "Não foi possível interpretar a resposta da IA. Tente novamente.");
        }
    }
 
    /**
     * Remove formatação markdown que o modelo às vezes insere mesmo quando instruído a não fazer.
     * Ex: ```json { ... } ``` → { ... }
     */
    private String limparMarkdown(String resposta) {
        return resposta
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }
 
    private String textOr(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asText();
    }
 
    private List<String> parsearLista(JsonNode root, String field) {
        JsonNode node = root.path(field);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }
 
    private CopomResponseDTO respostaDeErro(String trechoReferencia, String mensagem) {
        return new CopomResponseDTO(
                "NEUTRO", null, null, null, null,
                List.of(), null,
                trechoReferencia,
                true, mensagem
        );
    }
}