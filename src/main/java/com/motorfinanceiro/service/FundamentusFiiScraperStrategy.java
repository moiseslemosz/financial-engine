package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Extrator imbatível para FIIs e FI-Infras usando o Fundamentus.
 * Imune aos bloqueios de Cloudflare no ambiente do Codespace.
 */
@Component
public class FundamentusFiiScraperStrategy implements FiiScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(FundamentusFiiScraperStrategy.class);
    private static final String BASE_URL = "https://www.fundamentus.com.br/detalhes.php?papel=";

    @Override
    public FiiResponseDTO extractFiiData(String ticker) {
        String url = BASE_URL + ticker.toUpperCase();
        log.info("[FundamentusFII] Buscando HTML para: {}", ticker);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12000)
                    .get();

            if (doc.select("table.w728").isEmpty()) {
                throw new ScraperException("[FundamentusFII] Ticker não encontrado.");
            }

            BigDecimal price = extrairPorLabel(doc, "Cotação");
            BigDecimal dy    = extrairPorLabel(doc, "Div. Yield");
            BigDecimal pvp   = extrairPorLabel(doc, "P/VP");

            if (price == null) {
                throw new ScraperException("[FundamentusFII] Cotação não encontrada.");
            }

            log.info("[FundamentusFII] Sucesso: {} | Preço: {} | DY: {} | P/VP: {}", ticker, price, dy, pvp);

            return new FiiResponseDTO(
                    ticker.toUpperCase(),
                    price,
                    dy,
                    pvp,
                    LocalDateTime.now(),
                    getSourceName()
            );

        } catch (Exception e) {
            log.error("[FundamentusFII] Falha ao raspar HTML de {}: {}", ticker, e.getMessage());
            throw new ScraperException("[FundamentusFII] Erro de conexão para " + ticker, e);
        }
    }

    private BigDecimal extrairPorLabel(Document doc, String label) {
        Elements labels = doc.select("td.label span.txt, td.label");
        for (Element labelEl : labels) {
            if (labelEl.text().trim().equalsIgnoreCase(label)) {
                Element parent = labelEl.parent().tagName().equals("td") ? labelEl.parent() : labelEl;
                Element dataCell = parent.nextElementSibling();
                if (dataCell != null) {
                    return parseToBigDecimal(dataCell.text());
                }
            }
        }
        return null;
    }

    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-") || value.equalsIgnoreCase("N/D")) return null;
        try {
            String clean = value.replace("%", "").replace(".", "").replace(",", ".").trim();
            return new BigDecimal(clean);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getSourceName() {
        return "Fundamentus";
    }
}