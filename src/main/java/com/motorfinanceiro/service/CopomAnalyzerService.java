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
 
    public CopomResponseDTO analisar(String textoAta) {
        log.info("[COPOM] Iniciando análise. Tamanho: {} chars", textoAta.length());
        String trecho = textoAta.substring(0, Math.min(200, textoAta.length())) + "...";
 
        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String jsonBruto    = chatClient.prompt()
                    .system(systemPrompt)
                    .user(textoAta)
                    .call()
                    .content();
 
            log.debug("[COPOM] Resposta bruta: {}", jsonBruto);
            return parsear(jsonBruto, trecho);
 
        } catch (Exception e) {
            log.error("[COPOM] Falha ao chamar IA: {}", e.getMessage());
            return erro(trecho, "Serviço de IA indisponível: " + e.getMessage());
        }
    }
 
    private CopomResponseDTO parsear(String jsonBruto, String trecho) {
        try {
            String json = limpar(jsonBruto);
            JsonNode root = objectMapper.readTree(json);
 
            if (root.has("erro")) {
                return erro(trecho, root.get("erro").asText());
            }
 
            // Roteamento de portfólio (objeto aninhado)
            JsonNode rot = root.path("rotacaoPortfolio");
            String rotRF  = textOr(rot, "rendaFixa", "");
            String rotFii = textOr(rot, "fiis",       "");
            String rotAcao= textOr(rot, "acao",       "");
 
            return new CopomResponseDTO(
                    textOr(root, "vies",           "NEUTRO"),
                    textOr(root, "titulo",          ""),
                    textOr(root, "resumo",          ""),
                    textOr(root, "impactoRendaFixa",""),
                    textOr(root, "impactoFiis",     ""),
                    lista(root, "frasesChave"),
                    textOr(root, "perspectiva",     ""),
                    rotRF, rotFii, rotAcao,
                    trecho, false, null
            );
 
        } catch (Exception e) {
            log.error("[COPOM] Falha no parse: {}", e.getMessage());
            return erro(trecho, "Não foi possível interpretar a resposta da IA. Tente novamente.");
        }
    }
 
    private String limpar(String s) {
        return s.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
    }
 
    private String textOr(JsonNode root, String field, String def) {
        JsonNode n = root.path(field);
        return (n.isMissingNode() || n.isNull()) ? def : n.asText();
    }
 
    private List<String> lista(JsonNode root, String field) {
        JsonNode n = root.path(field);
        List<String> r = new ArrayList<>();
        if (n.isArray()) n.forEach(i -> r.add(i.asText()));
        return r;
    }
 
    private CopomResponseDTO erro(String trecho, String msg) {
        return new CopomResponseDTO(
                "NEUTRO", null, null, null, null,
                List.of(), null, null, null, null,
                trecho, true, msg
        );
    }
}