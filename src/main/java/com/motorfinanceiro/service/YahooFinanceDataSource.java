package com.motorfinanceiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Fonte PRIMÁRIA: Yahoo Finance (endpoint não-oficial).
 *
 * URL: https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.SA
 *
 * Cobertura de campos do AcaoResponseDTO:
 *   ✅ cotacao             (regularMarketPrice)
 *   ✅ pl                  (trailingPE)
 *   ✅ pvp                 (priceToBook)
 *   ✅ dividendYield       (trailingAnnualDividendYield — decimal, ex: 0.081)
 *   ✅ roe                 (returnOnEquity — decimal, ex: 0.232)
 *   ✅ margemLiquida       (profitMargins — decimal, ex: 0.15)
 *   ✅ margemEbit          (ebitdaMargins — aproximação)
 *   ✅ dividaBrutaPatrim   (debtToEquity — já em %)
 *   ✅ liqCorrente         (currentRatio)
 *   ⚠️ evEbitda            (enterpriseToEbitda — presente no v10, não no v8)
 *   ⚠️ crescRec5a          (revenueGrowth — anual, não 5 anos exatos)
 *   ❌ roic                (não disponível publicamente no Yahoo)
 *
 * IMPORTANTE: O Yahoo bloqueia periodicamente IPs de datacenter.
 * Sempre use como fonte primária mas espere falhas frequentes em Codespace.
 * O AcaoService trata isso via fallback automático.
 */
@Component
public class YahooFinanceDataSource implements AcaoDataSource {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceDataSource.class);

    // v8 para cotação + v10 para fundamentalistas (dois requests)
    private static final String URL_CHART = "https://query1.finance.yahoo.com/v8/finance/chart/%s.SA";
    private static final String URL_STATS  = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s.SA"
            + "?modules=defaultKeyStatistics,financialData,summaryDetail";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public AcaoResponseDTO fetch(String ticker) {
        String t = ticker.toUpperCase();
        log.info("[Yahoo Finance] Buscando dados para: {}", t);

        try {
            // Requisição 1: cotação básica
            JsonNode chart = requestJson(String.format(URL_CHART, t));
            JsonNode meta  = chart.path("chart").path("result").path(0).path("meta");

            BigDecimal cotacao = decimalOr(meta, "regularMarketPrice");
            if (cotacao == null) {
                throw new ScraperException("[Yahoo Finance] Cotação não encontrada para " + t
                        + " — ticker pode não existir na B3 ou endpoint bloqueado.");
            }

            // Requisição 2: fundamentalistas
            JsonNode summary    = requestJson(String.format(URL_STATS, t));
            JsonNode result     = summary.path("quoteSummary").path("result").path(0);
            JsonNode keyStats   = result.path("defaultKeyStatistics");
            JsonNode finData    = result.path("financialData");
            JsonNode sumDetail  = result.path("summaryDetail");

            BigDecimal pl             = rawValue(keyStats, "trailingEps") != null && cotacao != null
                    ? safeDiv(cotacao, rawValue(keyStats, "trailingEps"))
                    : rawValue(keyStats, "trailingPE");
            BigDecimal pvp            = rawValue(keyStats, "priceToBook");
            BigDecimal dy             = pctOrNull(sumDetail, "trailingAnnualDividendYield");
            BigDecimal roe            = pctOrNull(finData, "returnOnEquity");
            BigDecimal margemLiquida  = pctOrNull(finData, "profitMargins");
            BigDecimal margemEbit     = pctOrNull(finData, "ebitdaMargins");   // aproximação
            BigDecimal evEbitda       = rawValue(keyStats, "enterpriseToEbitda");
            BigDecimal dividaPatrim   = rawValue(finData, "debtToEquity");     // já em %
            BigDecimal crescRec       = pctOrNull(finData, "revenueGrowth");   // anual
            BigDecimal liqCorrente    = rawValue(finData, "currentRatio");

            log.info("[Yahoo Finance] Sucesso: {} | Cotação: {} | P/L: {} | P/VP: {} | DY: {}%",
                    t, cotacao, pl, pvp, dy);

            return new AcaoResponseDTO(
                    t, cotacao, pl, pvp, dy,
                    roe, null,              // roic indisponível no Yahoo
                    margemLiquida, margemEbit, evEbitda,
                    dividaPatrim, crescRec, liqCorrente,
                    getSourceName(), LocalDateTime.now()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Yahoo Finance] Falha para {}: {}", t, e.getMessage());
            throw new ScraperException("[Yahoo Finance] Erro ao buscar " + t + ": " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // NORMALIZAÇÃO DE ESCALA
    // =========================================================================

    /**
     * Yahoo retorna ROE, margens e DY como decimal (0.232 = 23,2%).
     * Multiplica por 100 para alinhar com o padrão do projeto (percentual).
     */
    private BigDecimal pctOrNull(JsonNode node, String field) {
        JsonNode raw = node.path(field).path("raw");
        if (raw.isMissingNode() || raw.isNull()) return null;
        try {
            return new BigDecimal(raw.asText())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retorna valores que já vêm na escala correta (cotação, P/L, P/VP, etc.).
     */
    private BigDecimal rawValue(JsonNode node, String field) {
        JsonNode raw = node.path(field).path("raw");
        if (raw.isMissingNode() || raw.isNull()) return null;
        try {
            return new BigDecimal(raw.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cotação vem direto no meta sem o wrapper {raw, fmt}.
     */
    private BigDecimal decimalOr(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal safeDiv(BigDecimal a, BigDecimal b) {
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) return null;
        return a.divide(b, 2, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // HTTP
    // =========================================================================

    private JsonNode requestJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status == 404) {
            throw new ScraperException("[Yahoo Finance] Ticker não encontrado (404).");
        }
        if (status == 429) {
            throw new ScraperException("[Yahoo Finance] Rate limit atingido (429).");
        }
        if (status != 200) {
            throw new ScraperException("[Yahoo Finance] HTTP " + status + " ao acessar " + url);
        }

        return objectMapper.readTree(response.body());
    }

    @Override
    public String getSourceName() { return "Yahoo Finance"; }

    @Override
    public int getPriority() { return 1; }
}