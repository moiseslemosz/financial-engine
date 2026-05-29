package com.motorfinanceiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.FiiAnaliseResponseDTO;
import com.motorfinanceiro.dto.FiiResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agente: Auditor de FIIs.
 *
 * Recebe os dados numéricos já calculados pelo motor Java (preço, DY, P/VP)
 * e usa o Gemini para gerar um veredito qualitativo em linguagem natural.
 */
@Service
public class FiiAuditorService {

    private static final Logger log = LoggerFactory.getLogger(FiiAuditorService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/fii-auditor.st")
    private Resource systemPromptResource;

    // CORREÇÃO 1: Injetar o Builder em vez da interface direta
    public FiiAuditorService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient   = chatClientBuilder.build(); 
        this.objectMapper = objectMapper;
    }

    public FiiAnaliseResponseDTO analisar(FiiResponseDTO fiiData) {
        log.info("[FiiAuditor] Analisando: {} | Preço: {} | DY: {}% | P/VP: {}",
                fiiData.ticker(), fiiData.currentPrice(),
                fiiData.dividendYield(), fiiData.pvp());

        try {
            String mensagemUsuario = formatarDadosParaIA(fiiData);

            // CORREÇÃO 2: Passar o Resource diretamente para o método .system()
            String jsonBruto = chatClient.prompt()
                    .system(systemPromptResource) // O Spring AI lê o arquivo nativamente!
                    .user(mensagemUsuario)
                    .call()
                    .content();

            log.debug("[FiiAuditor] Resposta bruta da IA: {}", jsonBruto);

            return parsearResposta(fiiData, jsonBruto);

        } catch (Exception e) {
            log.error("[FiiAuditor] Falha ao chamar a IA para {}: {}", fiiData.ticker(), e.getMessage());
            return respostaComErro(fiiData, "Serviço de IA indisponível: " + e.getMessage());
        }
    }

    // =========================================================================
    // FORMATAÇÃO DO PAYLOAD PARA A IA
    // =========================================================================

    private String formatarDadosParaIA(FiiResponseDTO fii) {
        return """
                Ticker: %s
                Preço atual: R$ %s
                Dividend Yield (último 12 meses): %s%%
                P/VP (Preço sobre Valor Patrimonial): %s
                Fonte dos dados: %s
                """.formatted(
                fii.ticker(),
                fii.currentPrice()    != null ? fii.currentPrice()    : "N/D",
                fii.dividendYield()   != null ? fii.dividendYield()   : "N/D",
                fii.pvp()             != null ? fii.pvp()             : "N/D",
                fii.source()
        );
    }

    // =========================================================================
    // PARSING DA RESPOSTA
    // =========================================================================

    private FiiAnaliseResponseDTO parsearResposta(FiiResponseDTO fiiData, String jsonBruto) {
        try {
            String jsonLimpo = limparMarkdown(jsonBruto);
            JsonNode root    = objectMapper.readTree(jsonLimpo);

            return new FiiAnaliseResponseDTO(
                    fiiData.ticker(),
                    fiiData.currentPrice(),
                    fiiData.dividendYield(),
                    fiiData.pvp(),
                    fiiData.source(),
                    fiiData.lastUpdated(),
                    textOr(root, "veredicto", "NEUTRO"),
                    textOr(root, "analise", ""),
                    parsearLista(root, "pontosFavoraveis"),
                    parsearLista(root, "pontosAtencao"),
                    textOr(root, "disclaimer",
                            "Este conteúdo é meramente informativo e não constitui recomendação de investimento."),
                    false,
                    null
            );

        } catch (Exception e) {
            log.error("[FiiAuditor] Falha ao parsear JSON da IA para {}: {}", fiiData.ticker(), e.getMessage());
            return respostaComErro(fiiData,
                    "Não foi possível interpretar a resposta da IA. Dados numéricos disponíveis acima.");
        }
    }

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

    private FiiAnaliseResponseDTO respostaComErro(FiiResponseDTO fiiData, String mensagem) {
        return new FiiAnaliseResponseDTO(
                fiiData.ticker(),
                fiiData.currentPrice(),
                fiiData.dividendYield(),
                fiiData.pvp(),
                fiiData.source(),
                fiiData.lastUpdated(),
                "NEUTRO", null,
                List.of(), List.of(),
                "Este conteúdo é meramente informativo.",
                true, mensagem
        );
    }
}