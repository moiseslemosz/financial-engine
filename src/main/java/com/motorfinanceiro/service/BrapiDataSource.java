package com.motorfinanceiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Fonte SECUNDÁRIA: brapi.dev
 *
 * URL: https://brapi.dev/api/quote/{ticker}?fundamental=true&token={token}
 *
 * Cobertura de campos do AcaoResponseDTO:
 *   ✅ cotacao             (regularMarketPrice)
 *   ✅ pl                  (priceEarnings)
 *   ✅ pvp                 (priceToBook)
 *   ✅ dividendYield       (dividendYield — já em %, ex: 8.1)
 *   ✅ roe                 (returnOnEquity — decimal, ex: 0.232 → convertido para %)
 *   ✅ margemLiquida       (profitMargin — decimal → convertido para %)
 *   ❌ roic                (não disponível)
 *   ❌ margemEbit          (não disponível)
 *   ❌ evEbitda            (não disponível no plano gratuito)
 *   ❌ dividaBrutaPatrim   (não disponível no plano gratuito)
 *   ❌ crescRec5a          (não disponível)
 *   ❌ liqCorrente         (não disponível)
 *
 * Campos ausentes retornam null — o AcaoAuditorService trata isso
 * na análise qualitativa informando limitação dos dados.
 *
 * Configuração: brapi.api.token no application.properties ou .env
 * Obtenha gratuitamente em: https://brapi.dev
 */
@Component
public class BrapiDataSource implements AcaoDataSource {

    private static final Logger log = LoggerFactory.getLogger(BrapiDataSource.class);

    private static final String BASE_URL =
            "https://brapi.dev/api/quote/%s?fundamental=true&token=%s";

    @Value("${brapi.api.token:}")
    private String apiToken;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public AcaoResponseDTO fetch(String ticker) {
        String t = ticker.toUpperCase();
        log.info("[brapi] Buscando dados para: {}", t);

        if (apiToken == null || apiToken.isBlank()) {
            throw new ScraperException(
                    "[brapi] Token não configurado. Defina brapi.api.token no .env.");
        }

        try {
            String url  = String.format(BASE_URL, t, apiToken);
            JsonNode root = requestJson(url);

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new ScraperException(
                        "[brapi] Ticker " + t + " não encontrado ou resposta vazia.");
            }

            JsonNode d = results.path(0);

            BigDecimal cotacao = decimalOr(d, "regularMarketPrice");
            if (cotacao == null) {
                throw new ScraperException(
                        "[brapi] Campo 'regularMarketPrice' ausente para " + t + ".");
            }

            BigDecimal pl            = decimalOr(d, "priceEarnings");
            BigDecimal pvp           = decimalOr(d, "priceToBook");
            // brapi retorna dividendYield já em % (ex: 8.10)
            BigDecimal dy            = decimalOr(d, "dividendYield");
            // returnOnEquity vem como decimal (ex: 0.232) — converte para %
            BigDecimal roe           = pctOrNull(d, "returnOnEquity");
            // profitMargin vem como decimal — converte para %
            BigDecimal margemLiquida = pctOrNull(d, "profitMargin");

            log.info("[brapi] Sucesso: {} | Cotação: {} | P/L: {} | P/VP: {} | DY: {}%",
                    t, cotacao, pl, pvp, dy);

            return new AcaoResponseDTO(
                    t, cotacao, pl, pvp, dy,
                    roe,   // roic = null (não disponível)
                    null,
                    margemLiquida,
                    null,  // margemEbit = null
                    null,  // evEbitda = null
                    null,  // dividaBrutaPatrim = null
                    null,  // crescRec5a = null
                    null,  // liqCorrente = null
                    getSourceName(), LocalDateTime.now()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[brapi] Falha para {}: {}", t, e.getMessage());
            throw new ScraperException("[brapi] Erro ao buscar " + t + ": " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // NORMALIZAÇÃO DE ESCALA
    // =========================================================================

    /**
     * Campos que vêm como decimal na brapi (ex: 0.232) → converte para % (23.2).
     */
    private BigDecimal pctOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Campos que já vêm na escala correta na brapi (cotação, P/L, DY, etc.).
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

    // =========================================================================
    // HTTP
    // =========================================================================

    private JsonNode requestJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new ScraperException("[brapi] Token inválido ou sem permissão (HTTP " + status + ").");
        }
        if (status == 404) {
            throw new ScraperException("[brapi] Ticker não encontrado (404).");
        }
        if (status == 429) {
            throw new ScraperException("[brapi] Limite de requisições atingido (429). " +
                    "Cota mensal: 1.000 req/mês no plano gratuito.");
        }
        if (status != 200) {
            throw new ScraperException("[brapi] HTTP " + status + " ao acessar a API.");
        }

        return objectMapper.readTree(response.body());
    }

    @Override
    public String getSourceName() { return "brapi.dev"; }

    @Override
    public int getPriority() { return 2; }
}