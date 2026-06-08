package com.motorfinanceiro.service;
 
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.FiiAnaliseResponseDTO;
import com.motorfinanceiro.dto.FiiAnaliseResponseDTO.AnaliseHistoricaDTO;
import com.motorfinanceiro.dto.FiiAnaliseResponseDTO.SimuladorFiiDTO;
import com.motorfinanceiro.dto.FiiResponseDTO;
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
 * Agente: Auditor de FIIs.
 *
 * Combina dados numéricos do motor Java com o conhecimento de treinamento
 * do Gemini para gerar análise estruturada equivalente ao agente original,
 * incluindo histórico, simulador de rendimento e veredito final.
 */
@Service
public class FiiAuditorService {
 
    private static final Logger log = LoggerFactory.getLogger(FiiAuditorService.class);
 
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
 
    @Value("classpath:prompts/fii-auditor.st")
    private Resource systemPromptResource;
 
    public FiiAuditorService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper      = objectMapper;
    }
 
    public FiiAnaliseResponseDTO analisar(FiiResponseDTO fiiData) {
        log.info("[FiiAuditor] Analisando: {} | Preço: {} | DY: {}% | P/VP: {}",
                fiiData.ticker(), fiiData.currentPrice(),
                fiiData.dividendYield(), fiiData.pvp());
 
        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userMsg      = formatarDados(fiiData);
 
            String jsonBruto = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userMsg)
                    .call()
                    .content();
 
            log.debug("[FiiAuditor] Resposta bruta: {}", jsonBruto);
            return parsear(fiiData, jsonBruto);
 
        } catch (Exception e) {
            log.error("[FiiAuditor] Falha ao chamar IA para {}: {}", fiiData.ticker(), e.getMessage());
            return erro(fiiData, "Serviço de IA indisponível: " + e.getMessage());
        }
    }
 
    // =========================================================================
    // FORMATAÇÃO DO INPUT PARA A IA
    // =========================================================================
 
    private String formatarDados(FiiResponseDTO fii) {
        return """
                Ticker: %s
                Preço atual: R$ %s
                Dividend Yield (últimos 12 meses): %s%%
                P/VP (Preço / Valor Patrimonial): %s
                Fonte dos dados: %s
                """.formatted(
                fii.ticker(),
                fii.currentPrice()  != null ? fii.currentPrice()  : "N/D",
                fii.dividendYield() != null ? fii.dividendYield() : "N/D",
                fii.pvp()           != null ? fii.pvp()           : "N/D",
                fii.source()
        );
    }
 
    // =========================================================================
    // PARSING DO JSON DA IA
    // =========================================================================
 
    private FiiAnaliseResponseDTO parsear(FiiResponseDTO fii, String jsonBruto) {
        try {
            JsonNode root = objectMapper.readTree(limpar(jsonBruto));
 
            return new FiiAnaliseResponseDTO(
                    fii.ticker(),
                    fii.currentPrice(),
                    fii.dividendYield(),
                    fii.pvp(),
                    fii.source(),
                    fii.lastUpdated(),
                    textOr(root, "tipo",                 "Desconhecido"),
                    textOr(root, "segmento",             ""),
                    textOr(root, "pvpStatus",            ""),
                    textOr(root, "dyAnalise",            ""),
                    textOr(root, "veredicto",            "NEUTRO"),
                    textOr(root, "veredictoStatus",      "EM_OBSERVACAO"),
                    textOr(root, "veredictoJustificativa",""),
                    textOr(root, "analise",              ""),
                    parsearHistorico(root.path("analiseHistorica")),
                    parsearSimulador(root.path("simulador")),
                    lista(root, "criteriosCondicionais"),
                    lista(root, "pontosFavoraveis"),
                    lista(root, "pontosAtencao"),
                    textOr(root, "disclaimer",
                            "Dados históricos baseados no conhecimento de treinamento da IA — " +
                            "podem estar desatualizados. Confirme nas fontes oficiais antes de qualquer decisão."),
                    false, null
            );
 
        } catch (Exception e) {
            log.error("[FiiAuditor] Falha no parse para {}: {}", fii.ticker(), e.getMessage());
            log.debug("[FiiAuditor] JSON que falhou: {}", jsonBruto);
            return erro(fii, "Não foi possível interpretar a resposta da IA. Tente novamente.");
        }
    }
 
    private AnaliseHistoricaDTO parsearHistorico(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        return new AnaliseHistoricaDTO(
                textOr(node, "resistenciaCrises",    "Não disponível"),
                textOr(node, "tendencia3Anos",       "Não disponível"),
                textOr(node, "patrimonioLiquido3Anos","Não disponível"),
                textOr(node, "pvpVsMedia",           "Não disponível")
        );
    }
 
    private SimuladorFiiDTO parsearSimulador(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        return new SimuladorFiiDTO(
                textOr(node, "dividendoPorCota",       "Dado não disponível"),
                textOr(node, "frequencia",             "Mensal"),
                textOr(node, "cotas1000",              "—"),
                textOr(node, "rendimentoPorPagamento", "—"),
                textOr(node, "rendimento12Meses",      "—"),
                textOr(node, "tendencia",              "")
        );
    }
 
    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================
 
    private String limpar(String s) {
        return s.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
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
 
    private FiiAnaliseResponseDTO erro(FiiResponseDTO fii, String msg) {
        return new FiiAnaliseResponseDTO(
                fii.ticker(), fii.currentPrice(), fii.dividendYield(),
                fii.pvp(), fii.source(), fii.lastUpdated(),
                "Desconhecido", "", "", "", "NEUTRO", "EM_OBSERVACAO", "",
                null, null, null,
                List.of(), List.of(), List.of(),
                "Este conteúdo é meramente informativo.", true, msg
        );
    }
}