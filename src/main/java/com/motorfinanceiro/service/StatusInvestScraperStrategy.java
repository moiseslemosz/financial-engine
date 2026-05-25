package com.motorfinanceiro.service;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
 
/**
 * Implementação da fonte PRIMÁRIA: StatusInvest (HTML scraping via Jsoup).
 *
 * Por que HTML e não JSON?
 * O StatusInvest usa SSR (Server-Side Rendering): Preço, DY e P/VP já chegam
 * "chumbados" no HTML da página. Não existe um endpoint JSON público que retorne
 * os três campos juntos — o único endpoint assíncrono visível no DevTools é o
 * `tickerprice`, que atualiza apenas a cotação em tempo real via websocket.
 *
 * --- Se os seletores pararem de funcionar ---
 * 1. Acesse https://statusinvest.com.br/fundos-imobiliarios/mxrf11 no Chrome
 * 2. Clique com o botão direito sobre o valor desejado → Inspecionar
 * 3. No painel Elements, clique com o botão direito no elemento azul
 *    → Copy → Copy selector
 * 4. Substitua o seletor correspondente abaixo
 */
@Component
@Qualifier("primaryStrategy")
public class StatusInvestScraperStrategy implements FiiScraperStrategy {
 
    private static final Logger log = LoggerFactory.getLogger(StatusInvestScraperStrategy.class);
 
    private static final String BASE_URL = "https://statusinvest.com.br/fundos-imobiliarios/";
 
    // Seletores CSS — ajuste se o site mudar a estrutura do HTML
    private static final String SELECTOR_PRICE = ".special .value";
    private static final String SELECTOR_DY    = "div[title*='Dividend Yield'] .value";
 
    @Override
    @Retryable(
            retryFor = ScraperException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 1500)
    )
    public FiiResponseDTO extractFiiData(String ticker) {
        String url = BASE_URL + ticker.toLowerCase();
        log.info("[StatusInvest] Buscando HTML para: {}", ticker);
 
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "max-age=0")
                    .header("Connection", "keep-alive")
                    .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://www.google.com/")
                    .timeout(15_000)
                    .get();
 
            BigDecimal price = parseToBigDecimal(extractTextBySelector(doc, SELECTOR_PRICE));
            BigDecimal dy    = parseToBigDecimal(extractTextBySelector(doc, SELECTOR_DY));
            BigDecimal pvp   = extractPvp(doc, ticker);
 
            if (price == null) {
                throw new ScraperException(
                        "[StatusInvest] Preço não encontrado no HTML para " + ticker
                        + ". Verifique se o seletor '" + SELECTOR_PRICE + "' ainda é válido.");
            }
 
            log.info("[StatusInvest] Sucesso: {} | Preço: {} | DY: {}% | P/VP: {}",
                    ticker, price, dy, pvp);
 
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
            log.error("[StatusInvest] Falha ao raspar HTML de {}: {}", ticker, e.getMessage());
            throw new ScraperException("[StatusInvest] Erro de conexão/parse para " + ticker, e);
        }
    }
 
    /**
     * Extração do P/VP com múltiplas estratégias.
     *
     * O P/VP não tem um atributo title único no HTML do StatusInvest, então
     * usamos a abordagem de buscar por texto — percorremos todos os elementos
     * que contêm "P/VP" e pegamos o .value irmão mais próximo.
     *
     * Estratégia 1: busca por atributo title exato
     * Estratégia 2: percorre divs por texto e pega o strong.value irmão
     */
    private BigDecimal extractPvp(Document doc, String ticker) {
        // Estratégia 1: atributo title
        String pvpStr = extractTextBySelector(doc, "div[title='P/VP'] .value");
        if (pvpStr != null) return parseToBigDecimal(pvpStr);
 
        // Estratégia 2: busca por texto no label e pega o valor do elemento irmão
        Elements labels = doc.select(".indicator .title");
        for (Element label : labels) {
            if (label.text().equalsIgnoreCase("P/VP")) {
                Element valueEl = label.parent().selectFirst(".value");
                if (valueEl != null) return parseToBigDecimal(valueEl.text());
            }
        }
 
        log.warn("[StatusInvest] P/VP não encontrado para {}. Retornando null.", ticker);
        return null;
    }
 
    private String extractTextBySelector(Document doc, String cssSelector) {
        Element element = doc.selectFirst(cssSelector);
        return element != null ? element.text() : null;
    }
 
    /**
     * Converte string no formato brasileiro (ex: "9,85", "12,50%", "R$ 1.000,50")
     * para BigDecimal.
     */
    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return null;
        try {
            String clean = value
                    .replace("%",  "")
                    .replace("R$", "")
                    .replace(".",  "")   // remove separador de milhar: 1.000 → 1000
                    .replace(",",  ".")  // troca decimal: 1000,50 → 1000.50
                    .trim();
            return clean.isEmpty() ? null : new BigDecimal(clean);
        } catch (NumberFormatException e) {
            log.warn("[StatusInvest] Não foi possível converter '{}' para BigDecimal", value);
            return null;
        }
    }
 
    @Override
    public String getSourceName() {
        return "StatusInvest";
    }
}