package com.motorfinanceiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Fonte de dados de FIIs via API (brapi.dev) — complementa as fontes
 * de scraping (StatusInvest, FundsExplorer, FundamentusFii, Investidor10).
 *
 * A brapi.dev também cobre FIIs pelo mesmo endpoint de cotação usado
 * para ações, retornando preço e dividendYield. O P/VP nem sempre
 * está disponível para FIIs no plano gratuito — quando ausente,
 * o FiiService segue para a próxima fonte da cadeia.
 *
 * Vantagem sobre o scraping: contrato de API estável, sem risco de
 * mudança de HTML quebrar a extração da noite para o dia.
 *
 * Reutiliza o mesmo token configurado para BrapiDataSource (ações).
 */
@Component
@Qualifier("brapiFiiStrategy")
public class BrapiFiiDataSource implements FiiScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(BrapiFiiDataSource.class);

    private static final String BASE_URL =
            "https://brapi.dev/api/quote/%s?fundamental=true&token=%s";

    @Value("${brapi.api.token:}")
    private String apiToken;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public FiiResponseDTO extractFiiData(String ticker) {
        String t = ticker.toUpperCase();
        log.info("[brapi FII] Buscando dados para: {}", t);

        if (apiToken == null || apiToken.isBlank()) {
            throw new ScraperException(
                    "[brapi FII] Token não configurado. Defina brapi.api.token no .env.");
        }

        try {
            String url = String.format(BASE_URL, t, apiToken);
            JsonNode root = requestJson(url);

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new ScraperException(
                        "[brapi FII] Ticker " + t + " não encontrado ou resposta vazia.");
            }

            JsonNode d = results.path(0);

            BigDecimal preco = decimalOr(d, "regularMarketPrice");
            if (preco == null) {
                throw new ScraperException(
                        "[brapi FII] Campo 'regularMarketPrice' ausente para " + t + ".");
            }

            // brapi retorna dividendYield já em % para FIIs
            BigDecimal dy  = decimalOr(d, "dividendYield");
            // P/VP nem sempre disponível para FIIs no plano gratuito
            BigDecimal pvp = decimalOr(d, "priceToBook");

            log.info("[brapi FII] Sucesso: {} | Preço: {} | DY: {} | P/VP: {}",
                    t, preco, dy, pvp);

            return new FiiResponseDTO(
                    t, preco, dy, pvp,
                    LocalDateTime.now(),
                    getSourceName()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[brapi FII] Falha para {}: {}", t, e.getMessage());
            throw new ScraperException("[brapi FII] Erro ao buscar " + t + ": " + e.getMessage(), e);
        }
    }

    private BigDecimal decimalOr(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

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
            throw new ScraperException("[brapi FII] Token inválido (HTTP " + status + ").");
        }
        if (status == 404) {
            throw new ScraperException("[brapi FII] Ticker não encontrado (404).");
        }
        if (status == 429) {
            throw new ScraperException("[brapi FII] Limite de requisições atingido (429).");
        }
        if (status != 200) {
            throw new ScraperException("[brapi FII] HTTP " + status + " ao acessar a API.");
        }

        return objectMapper.readTree(response.body());
    }

    @Override
    public String getSourceName() { return "brapi.dev"; }
}