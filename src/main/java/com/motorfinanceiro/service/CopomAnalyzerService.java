package com.motorfinanceiro.service;
 
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.CopomResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
 
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
 
/**
 * Agente: Tradutor do COPOM.
 * Usa AiFallbackService para chamadas resilientes com fallback automático de modelo.
 */
@Service
public class CopomAnalyzerService {
 
    private static final Logger log = LoggerFactory.getLogger(CopomAnalyzerService.class);
 
    private final AiFallbackService aiFallbackService;
    private final ObjectMapper objectMapper;
 
    @Value("classpath:prompts/copom-translator.st")
    private Resource systemPromptResource;
 
    public CopomAnalyzerService(AiFallbackService aiFallbackService, ObjectMapper objectMapper) {
        this.aiFallbackService = aiFallbackService;
        this.objectMapper      = objectMapper;
    }
 
    public CopomResponseDTO analisar(String textoAta) {
        log.info("[COPOM] Iniciando análise. Tamanho: {} chars", textoAta.length());
        String trecho = textoAta.substring(0, Math.min(200, textoAta.length())) + "...";
 
        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
 
            // AiFallbackService trata cota esgotada e timeout automaticamente
            String jsonBruto = aiFallbackService.call(systemPrompt, textoAta);
 
            log.debug("[COPOM] Resposta bruta: {}", jsonBruto);
            return parsear(jsonBruto, trecho);
 
        } catch (com.motorfinanceiro.exception.AiQuotaExceededException e) {
            log.error("[COPOM] Cadeia de fallback esgotada: {}", e.getMessage());
            return erro(trecho, "Serviço de IA temporariamente indisponível. Tente novamente em alguns minutos.");
        } catch (Exception e) {
            log.error("[COPOM] Falha inesperada: {}", e.getMessage());
            return erro(trecho, "Erro ao processar análise: " + e.getMessage());
        }
    }
 
    private CopomResponseDTO parsear(String jsonBruto, String trecho) {
        try {
            String json  = limpar(jsonBruto);
            JsonNode root = objectMapper.readTree(json);
 
            if (root.has("erro")) {
                return erro(trecho, root.get("erro").asText());
            }
 
            JsonNode rot    = root.path("rotacaoPortfolio");
            return new CopomResponseDTO(
                    textOr(root, "vies",            "NEUTRO"),
                    textOr(root, "titulo",          ""),
                    textOr(root, "resumo",          ""),
                    textOr(root, "impactoRendaFixa",""),
                    textOr(root, "impactoFiis",     ""),
                    lista(root, "frasesChave"),
                    textOr(root, "perspectiva",     ""),
                    textOr(rot,  "rendaFixa",       ""),
                    textOr(rot,  "fiis",            ""),
                    textOr(rot,  "acao",            ""),
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