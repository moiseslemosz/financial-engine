package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Qualifier("fallbackStrategy")
public class FundsExplorerScraperStrategy implements FiiScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(FundsExplorerScraperStrategy.class);
    private static final String BASE_URL = "https://www.fundsexplorer.com.br/funds/";

    @Override
    @Retryable(
            retryFor = ScraperException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 1500)
    )
    public FiiResponseDTO extractFiiData(String ticker) {
        String url = BASE_URL + ticker.toLowerCase();
        log.info("[FundsExplorer] Buscando HTML para: {}", ticker);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(15_000)
                    .get();

            // Lógica resiliente baseada em texto, não em classes CSS exatas
            // 1. Tenta PRIMEIRO os seletores exatos que você pegou no Chrome
            String priceStr = extractTextBySelector(doc, "#carbon_fields_fiis_header-2 > div > div > div.headerTicker__content > div.headerTicker__content__price > p");
            String dyStr = extractTextBySelector(doc, "#indicators > div:nth-child(3) > p:nth-child(2) > b");
            String pvpStr = extractTextBySelector(doc, "#indicators div:contains(P/VP) + div"); 

            // 2. Se algum vier nulo (caso o site mude no futuro), aciona o Fallback genérico
            if (priceStr == null) priceStr = extractByNeighborText(doc, "Cotação Atual", "Preço");
            if (dyStr == null) dyStr = extractByNeighborText(doc, "Dividend Yield", "DY");
            if (pvpStr == null) pvpStr = extractByNeighborText(doc, "P/VP");

            BigDecimal price = parseToBigDecimal(priceStr);
            BigDecimal dy = parseToBigDecimal(dyStr);
            BigDecimal pvp = parseToBigDecimal(pvpStr);

            if (price == null) {
                throw new ScraperException("[FundsExplorer] Valores não encontrados no HTML para " + ticker);
            }

            log.info("[FundsExplorer] Sucesso: {} | Preço: {} | DY: {} | P/VP: {}", ticker, price, dy, pvp);

            return new FiiResponseDTO(
                    ticker.toUpperCase(),
                    price,
                    dy,
                    pvp,
                    LocalDateTime.now(),
                    getSourceName()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FundsExplorer] Falha ao raspar HTML de {}: {}", ticker, e.getMessage());
            throw new ScraperException("[FundsExplorer] Erro de conexão/parse para " + ticker, e);
        }
    }

    private String extractByNeighborText(Document doc, String... searchTerms) {
        for (String term : searchTerms) {
            Elements labels = doc.getElementsContainingOwnText(term);
            if (!labels.isEmpty()) {
                // Pega o elemento logo após o label (geralmente onde fica o valor)
                return labels.first().parent().text().replace(term, "").trim();
            }
        }
        return null;
    }

    private String extractTextBySelector(Document doc, String cssSelector) {
        org.jsoup.nodes.Element element = doc.selectFirst(cssSelector);
        return element != null ? element.text() : null;
    }

    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-") || value.equals("N/A")) return null;
        try {
            String cleanValue = value.replace("%", "")
                    .replace("R$", "")
                    .replace(".", "")  // Remove separador de milhar
                    .replace(",", ".") // Troca vírgula decimal por ponto
                    .trim();
            // Lida com casos onde o texto extraído vem com lixo junto (ex: "Cotação Atual R$ 10,50")
            String[] parts = cleanValue.split(" ");
            cleanValue = parts[parts.length - 1]; 
            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getSourceName() {
        return "FundsExplorer";
    }
}