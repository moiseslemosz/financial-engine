package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Order(3) // Garante que será chamado no fallback, após StatusInvest e FundsExplorer
public class Investidor10ScraperStrategy implements FiiScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(Investidor10ScraperStrategy.class);
    private static final String BASE_URL = "https://investidor10.com.br/fiis/";

    @Override
    public FiiResponseDTO extractFiiData(String ticker) {
        String url = BASE_URL + ticker.toLowerCase() + "/";
        log.info("[Investidor10] Buscando HTML para: {}", ticker);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            BigDecimal price = extrairValor(doc, "Cotação");
            BigDecimal dy = extrairValor(doc, "DY (12M)");
            BigDecimal pvp = extrairValor(doc, "P/VP");

            if (price == null) {
                throw new ScraperException("[Investidor10] Cotação não encontrada para " + ticker);
            }

            log.info("[Investidor10] Sucesso: {} | Preço: {} | DY: {} | P/VP: {}", ticker, price, dy, pvp);

            return new FiiResponseDTO(
                    ticker.toUpperCase(),
                    price,
                    dy,
                    pvp,
                    LocalDateTime.now(),
                    getSourceName()
            );

        } catch (Exception e) {
            log.error("[Investidor10] Falha ao raspar HTML de {}: {}", ticker, e.getMessage());
            throw new ScraperException("[Investidor10] Erro de conexão/parse para " + ticker, e);
        }
    }

    private BigDecimal extrairValor(Document doc, String label) {
        for (Element card : doc.select("div._card")) {
            Element header = card.selectFirst("div._card-header span");
            if (header != null && header.text().trim().equalsIgnoreCase(label)) {
                Element valueEl = card.selectFirst("div._card-body span");
                if (valueEl != null) {
                    return parseToBigDecimal(valueEl.text());
                }
            }
        }
        return null;
    }

    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-") || value.equalsIgnoreCase("N/A")) {
            return null;
        }
        try {
            String clean = value.replace("R$", "").replace("%", "").replace(".", "").replace(",", ".").trim();
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getSourceName() {
        return "Investidor10";
    }
}