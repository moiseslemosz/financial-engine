package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fonte de dados fundamentalistas: Fundamentus (fundamentus.com.br).
 *
 * Por que Fundamentus e não StatusInvest para ações?
 * - Sem proteção Cloudflare — acessível diretamente do Codespace
 * - Página única por ticker com todos os indicadores fundamentalistas
 * - HTML estável e simples de parsear (tabelas <table> tradicionais)
 *
 * Estrutura do HTML: o Fundamentus organiza os dados em pares
 * <td class="label">RÓTULO</td><td class="data">VALOR</td>
 * dentro de várias tabelas na mesma página. A extração é feita
 * buscando o label exato e pegando o valor da célula seguinte.
 */
@Component
public class FundamentusScraperStrategy implements AcaoScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(FundamentusScraperStrategy.class);

    private static final String BASE_URL = "https://www.fundamentus.com.br/detalhes.php?papel=";

    @Override
    @Retryable(
            retryFor = ScraperException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 1500)
    )
    public AcaoResponseDTO extractAcaoData(String ticker) {
        String url = BASE_URL + ticker.toUpperCase();
        log.info("[Fundamentus] Buscando HTML para: {}", ticker);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12_000)
                    .get();

            // Validação: papel inexistente retorna página vazia/erro
            if (doc.select("table.w728").isEmpty()) {
                throw new ScraperException(
                        "[Fundamentus] Ticker " + ticker + " não encontrado ou página indisponível.");
            }

            BigDecimal cotacao          = extrairPorLabel(doc, "Cotação");
            BigDecimal pl               = extrairPorLabel(doc, "P/L");
            BigDecimal pvp              = extrairPorLabel(doc, "P/VP");
            BigDecimal dy               = extrairPorLabel(doc, "Div. Yield");
            BigDecimal roe              = extrairPorLabel(doc, "ROE");
            BigDecimal roic             = extrairPorLabel(doc, "ROIC");
            BigDecimal margemLiquida    = extrairPorLabel(doc, "Marg. Líquida");
            BigDecimal margemEbit       = extrairPorLabel(doc, "Marg. EBIT");
            BigDecimal evEbitda         = extrairPorLabel(doc, "EV / EBITDA");
            BigDecimal dividaBrutaPatrim= extrairPorLabel(doc, "Dív Líq / Patrim");
            BigDecimal crescRec5a       = extrairPorLabel(doc, "Cres. Rec (5a)");
            BigDecimal liqCorrente      = extrairPorLabel(doc, "Liquidez Corr");

            if (cotacao == null) {
                throw new ScraperException(
                        "[Fundamentus] Cotação não encontrada para " + ticker
                        + ". O ticker pode não existir ou o HTML mudou de estrutura.");
            }

            log.info("[Fundamentus] Sucesso: {} | Cotação: {} | P/L: {} | P/VP: {} | DY: {}%",
                    ticker, cotacao, pl, pvp, dy);

            return new AcaoResponseDTO(
                    ticker.toUpperCase(),
                    cotacao, pl, pvp, dy, roe, roic,
                    margemLiquida, margemEbit, evEbitda,
                    dividaBrutaPatrim, crescRec5a, liqCorrente,
                    getSourceName(),
                    LocalDateTime.now()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Fundamentus] Falha ao raspar HTML de {}: {}", ticker, e.getMessage());
            throw new ScraperException("[Fundamentus] Erro de conexão/parse para " + ticker, e);
        }
    }

    /**
     * Extrai um valor numérico buscando pelo rótulo exato na coluna .label
     * e capturando o conteúdo da célula .data correspondente.
     *
     * O Fundamentus repete alguns rótulos em tabelas diferentes (ex: "Cotação"
     * aparece em mais de um contexto) — para evitar ambiguidade, a busca usa
     * correspondência exata de texto e pega a primeira ocorrência válida.
     */
    private BigDecimal extrairPorLabel(Document doc, String label) {
        Elements labels = doc.select("td.label span.txt, td.label");

        for (Element labelEl : labels) {
            String texto = labelEl.text().trim();
            if (texto.equalsIgnoreCase(label)) {
                Element parent = labelEl.parent().tagName().equals("td") ? labelEl.parent() : labelEl;
                Element dataCell = parent.nextElementSibling();

                if (dataCell != null) {
                    // O valor pode estar direto no <td> ou dentro de um <span>
                    String valor = dataCell.text().trim();
                    BigDecimal parsed = parseToBigDecimal(valor);
                    if (parsed != null) return parsed;
                }
            }
        }

        log.warn("[Fundamentus] Label '{}' não encontrado ou sem valor numérico válido.", label);
        return null;
    }

    /**
     * Converte string no formato brasileiro do Fundamentus para BigDecimal.
     * Trata formatos como "12,34", "8,50%", "1.234,56", "-3,21%".
     */
    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-") || value.equalsIgnoreCase("N/D")) {
            return null;
        }
        try {
            String clean = value
                    .replace("%", "")
                    .replace(".", "")   // remove separador de milhar
                    .replace(",", ".")  // decimal BR → decimal US
                    .trim();
            if (clean.isEmpty() || clean.equals("-")) return null;
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            log.debug("[Fundamentus] Não foi possível converter '{}' para BigDecimal", value);
            return null;
        }
    }

    @Override
    public String getSourceName() {
        return "Fundamentus";
    }
}