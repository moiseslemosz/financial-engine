package com.motorfinanceiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.AcaoAnaliseResponseDTO;
import com.motorfinanceiro.dto.AcaoAnaliseResponseDTO.AnaliseHistoricaAcaoDTO;
import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.AiQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Agente: Analista de Ações.
 *
 * Recebe os dados fundamentalistas calculados pelo Fundamentus (motor Java)
 * e usa o Gemini via AiFallbackService para gerar análise qualitativa completa:
 * valuação (Graham, Bazin), qualidade da empresa, histórico, veredito.
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  Os números vêm do Fundamentus — a IA só interpreta e calcula  ║
 * ║  preços justos com base nos dados recebidos. Nunca inventa.    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Service
public class AcaoAuditorService {

    private static final Logger log = LoggerFactory.getLogger(AcaoAuditorService.class);

    private final AiFallbackService aiFallbackService;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/acao-analyst.st")
    private Resource systemPromptResource;

    public AcaoAuditorService(AiFallbackService aiFallbackService, ObjectMapper objectMapper) {
        this.aiFallbackService = aiFallbackService;
        this.objectMapper      = objectMapper;
    }

    /**
     * Combina dados fundamentalistas do motor Java com análise da IA.
     *
     * @param acaoData Dados extraídos pelo FundamentusScraperStrategy
     * @return Análise completa com valuação, qualidade, histórico e veredito
     */
    public AcaoAnaliseResponseDTO analisar(AcaoResponseDTO acaoData) {
        log.info("[AcaoAuditor] Analisando: {} | Cotação: {} | P/L: {} | P/VP: {} | ROE: {}%",
                acaoData.ticker(), acaoData.cotacao(),
                acaoData.pl(), acaoData.pvp(), acaoData.roe());

        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userMsg      = formatarDados(acaoData);

            String jsonBruto = aiFallbackService.call(systemPrompt, userMsg);
            log.debug("[AcaoAuditor] Resposta bruta: {}", jsonBruto);

            return parsear(acaoData, jsonBruto);

        } catch (AiQuotaExceededException e) {
            log.error("[AcaoAuditor] Cadeia de fallback esgotada para {}: {}", acaoData.ticker(), e.getMessage());
            return erro(acaoData, "Serviço de IA temporariamente indisponível. Tente novamente em alguns minutos.");
        } catch (Exception e) {
            log.error("[AcaoAuditor] Falha inesperada para {}: {}", acaoData.ticker(), e.getMessage());
            return erro(acaoData, "Erro ao processar análise: " + e.getMessage());
        }
    }

    // =========================================================================
    // FORMATAÇÃO DO PAYLOAD PARA A IA
    // =========================================================================

    private String formatarDados(AcaoResponseDTO a) {
        return """
                Ticker: %s
                Cotação atual: R$ %s
                P/L (Preço / Lucro): %s
                P/VP (Preço / Valor Patrimonial): %s
                Dividend Yield (12 meses): %s%%
                ROE (Retorno sobre Patrimônio): %s%%
                ROIC (Retorno sobre Capital Investido): %s%%
                Margem Líquida: %s%%
                Margem EBIT: %s%%
                EV/EBITDA: %s
                Dívida Bruta / Patrimônio Líquido: %s
                Crescimento de Receita 5 anos: %s%%
                Liquidez Corrente: %s
                Fonte dos dados: %s
                """.formatted(
                a.ticker(),
                val(a.cotacao()),  val(a.pl()),     val(a.pvp()),
                val(a.dividendYield()), val(a.roe()), val(a.roic()),
                val(a.margemLiquida()), val(a.margemEbit()),
                val(a.evEbitda()),  val(a.dividaBrutaPatrim()),
                val(a.crescRec5a()), val(a.liqCorrente()),
                a.source()
        );
    }

    private String val(Object v) { return v != null ? v.toString() : "N/D"; }

    // =========================================================================
    // PARSING DO JSON DA IA
    // =========================================================================

    private AcaoAnaliseResponseDTO parsear(AcaoResponseDTO a, String jsonBruto) {
        try {
            JsonNode root = objectMapper.readTree(limpar(jsonBruto));

            return new AcaoAnaliseResponseDTO(
                    a.ticker(), a.cotacao(), a.pl(), a.pvp(),
                    a.dividendYield(), a.roe(), a.roic(),
                    a.margemLiquida(), a.margemEbit(), a.evEbitda(),
                    a.dividaBrutaPatrim(), a.crescRec5a(), a.liqCorrente(),
                    a.source(), a.lastUpdated(),
                    textOr(root, "setor",      "Não identificado"),
                    textOr(root, "segmento",   ""),
                    textOr(root, "empresa",    a.ticker()),
                    textOr(root, "precoJustoGraham", "N/A"),
                    textOr(root, "precoJustoBazin",  "N/A"),
                    textOr(root, "margemSeguranca",  ""),
                    textOr(root, "veredicto",        "NEUTRO"),
                    textOr(root, "veredictoStatus",  "EM_OBSERVACAO"),
                    textOr(root, "veredictoJustificativa", ""),
                    textOr(root, "analiseValuacao",  ""),
                    textOr(root, "analiseQualidade", ""),
                    textOr(root, "analiseRisco",     ""),
                    parsearHistorico(root.path("analiseHistorica")),
                    lista(root, "pontosFavoraveis"),
                    lista(root, "pontosAtencao"),
                    textOr(root, "disclaimer",
                            "Análise baseada em dados do Fundamentus e conhecimento de treinamento da IA. " +
                            "Não constitui recomendação de investimento. A decisão final é sempre do investidor."),
                    false, null
            );
        } catch (Exception e) {
            log.error("[AcaoAuditor] Falha no parse para {}: {}", a.ticker(), e.getMessage());
            return erro(a, "Não foi possível interpretar a resposta da IA. Tente novamente.");
        }
    }

    private AnaliseHistoricaAcaoDTO parsearHistorico(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        return new AnaliseHistoricaAcaoDTO(
                textOr(node, "resistenciaCrises",  "Não disponível"),
                textOr(node, "tendenciaLucros",    "Não disponível"),
                textOr(node, "politicaDividendos", "Não disponível")
        );
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

    private AcaoAnaliseResponseDTO erro(AcaoResponseDTO a, String msg) {
        return new AcaoAnaliseResponseDTO(
                a.ticker(), a.cotacao(), a.pl(), a.pvp(),
                a.dividendYield(), a.roe(), a.roic(),
                a.margemLiquida(), a.margemEbit(), a.evEbitda(),
                a.dividaBrutaPatrim(), a.crescRec5a(), a.liqCorrente(),
                a.source(), a.lastUpdated(),
                "Não identificado", "", a.ticker(),
                "N/A", "N/A", "",
                "NEUTRO", "EM_OBSERVACAO", "", "", "", "",
                null,
                List.of(), List.of(),
                "Este conteúdo é meramente informativo.",
                true, msg
        );
    }
}