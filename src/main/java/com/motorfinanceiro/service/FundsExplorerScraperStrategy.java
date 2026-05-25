package com.motorfinanceiro.service;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
 
/**
 * Implementação da fonte SECUNDÁRIA (fallback): Funds Explorer.
 *
 * Utiliza scraping de HTML com Jsoup, o que é mais resiliente do que depender
 * de endpoints internos não documentados. O trade-off é que os seletores CSS
 * podem quebrar caso o site altere sua estrutura visual.
 *
 * --- Como atualizar os seletores CSS se pararem de funcionar ---
 * 1. Acesse: https://www.fundsexplorer.com.br/funds/MXRF11
 * 2. Clique com o botão direito no valor que precisa (ex: Cotação) → Inspecionar
 * 3. Copie o seletor CSS do elemento → substitua a constante correspondente abaixo
 */
@Component
@Qualifier("fallbackStrategy")
public class FundsExplorerScraperStrategy implements FiiScraperStrategy {
 
    private static final Logger log = LoggerFactory.getLogger(FundsExplorerScraperStrategy.class);
 
    private static final String BASE_URL = "https://www.fundsexplorer.com.br/funds/";
 
    // Seletores CSS — verifique e ajuste conforme o HTML atual do site
    private static final String SELECTOR_PRICE = ".indicator-value p.value";
    private static final String SELECTOR_DY    = ".dy-indicator .value";
    private static final String SELECTOR_PVP   = ".pvp-indicator .value";
 
    @Override
    @Retryable(
            retryFor = ScraperException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 2000)
    )
    public FiiResponseDTO extractFiiData(String ticker) {
        String url = BASE_URL + ticker.toLowerCase();
        log.info("[FundsExplorer] Buscando dados para: {}", ticker);
 
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12_000)
                    .get();
 
            BigDecimal price = extractValue(doc, SELECTOR_PRICE, "price", ticker);
            BigDecimal dy    = extractValue(doc, SELECTOR_DY, "dy", ticker);
            BigDecimal pvp   = extractValue(doc, SELECTOR_PVP, "pvp", ticker);
 
            if (price == null) {
                throw new ScraperException(
                        "[FundsExplorer] Campo 'price' não encontrado para " + ticker
                        + ". Verifique se os seletores CSS ainda são válidos.");
            }
 
            log.info("[FundsExplorer] Dados extraídos com sucesso: {} | Preço: {} | DY: {}%",
                    ticker, price, dy);
 
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
            log.error("[FundsExplorer] Falha ao buscar dados de {}: {}", ticker, e.getMessage());
            throw new ScraperException(
                    "[FundsExplorer] Erro ao buscar ticker " + ticker + ": " + e.getMessage(), e);
        }
    }
 
    /**
     * Extrai e converte o valor de um elemento HTML para BigDecimal.
     * Trata formatos brasileiros como "R$ 9,85" e "12,5%".
     */
    private BigDecimal extractValue(Document doc, String selector, String fieldName, String ticker) {
        Element element = doc.selectFirst(selector);
 
        if (element == null) {
            log.warn("[FundsExplorer] Seletor '{}' não encontrou nenhum elemento para {}. "
                    + "O site pode ter mudado sua estrutura.", selector, ticker);
            return null;
        }
 
        String raw = element.text()
                .replaceAll("R\\$", "")
                .replaceAll("%", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();
 
        try {
            return raw.isEmpty() ? null : new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("[FundsExplorer] Não foi possível converter '{}' para o campo '{}' do ticker {}",
                    raw, fieldName, ticker);
            return null;
        }
    }
 
    @Override
    public String getSourceName() {
        return "FundsExplorer";
    }
}