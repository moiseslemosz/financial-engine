package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;
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
 * Fonte TERCIÁRIA (fallback final): Fundamentus — scraping HTML.
 *
 * Agora implementa AcaoDataSource em vez de AcaoScraperStrategy,
 * alinhando ao padrão unificado da cadeia de fallback.
 *
 * Cobertura: 12/12 campos (mais completa das três fontes).
 * Sem autenticação, sem rate limit explícito, sem SLA.
 *
 * Labels corrigidos em relação à versão anterior:
 *   "Marg. Líquida" → "Mrg. Líq."
 *   "Marg. EBIT"    → "Mrg. Ebit"
 *   "EV/EBITDA"     → "EV/EBIT"   (Fundamentus usa EBIT, não EBITDA)
 *   "Dív.Bruta/Patrim." → "Dív. Bruta/Patrim."
 *   "Liquidez Corrente" → "Liq. Corrente"
 */
@Component
public class FundamentusScraperStrategy implements AcaoDataSource {

    private static final Logger log = LoggerFactory.getLogger(FundamentusScraperStrategy.class);

    private static final String BASE_URL = "https://www.fundamentus.com.br/detalhes.php?papel=";

    @Override
    public AcaoResponseDTO fetch(String ticker) {
        return extractAcaoData(ticker);
    }

    public AcaoResponseDTO extractAcaoData(String ticker) {
        String t   = ticker.toUpperCase();
        String url = BASE_URL + t;
        log.info("[Fundamentus] Buscando HTML para: {}", t);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12_000)
                    .get();

            if (doc.select("table.w728").isEmpty()) {
                throw new ScraperException(
                        "[Fundamentus] Ticker " + t + " não encontrado (404).");
            }

            BigDecimal cotacao           = extrairPorLabel(doc, "Cotação");
            BigDecimal pl                = extrairPorLabel(doc, "P/L");
            BigDecimal pvp               = extrairPorLabel(doc, "P/VP");
            BigDecimal dy                = extrairPorLabel(doc, "Div. Yield");
            BigDecimal roe               = extrairPorLabel(doc, "ROE");
            BigDecimal roic              = extrairPorLabel(doc, "ROIC");
            BigDecimal margemLiquida     = extrairPorLabel(doc, "Mrg. Líq.");
            BigDecimal margemEbit        = extrairPorLabel(doc, "Mrg. Ebit");
            BigDecimal evEbit            = extrairPorLabel(doc, "EV/EBIT");
            BigDecimal dividaBrutaPatrim = extrairPorLabel(doc, "Dív. Bruta/Patrim.");
            BigDecimal crescRec5a        = extrairPorLabel(doc, "Cresc. Rec.5a");
            BigDecimal liqCorrente       = extrairPorLabel(doc, "Liq. Corrente");

            if (cotacao == null) {
                throw new ScraperException(
                        "[Fundamentus] Cotação não encontrada para " + t
                        + ". Verifique se os seletores HTML ainda são válidos.");
            }

            log.info("[Fundamentus] Sucesso: {} | Cotação: {} | P/L: {} | P/VP: {} | DY: {}%",
                    t, cotacao, pl, pvp, dy);

            return new AcaoResponseDTO(
                    t, cotacao, pl, pvp, dy,
                    roe, roic,
                    margemLiquida, margemEbit, evEbit,
                    dividaBrutaPatrim, crescRec5a, liqCorrente,
                    getSourceName(), LocalDateTime.now()
            );

        } catch (ScraperException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Fundamentus] Falha para {}: {}", t, e.getMessage());
            throw new ScraperException("[Fundamentus] Erro de conexão/parse para " + t, e);
        }
    }

    // =========================================================================
    // EXTRAÇÃO DOS LABELS
    // =========================================================================

    /**
     * Percorre todas as células de label da página e retorna o valor
     * da célula data correspondente ao label exato informado.
     *
     * O Fundamentus organiza os dados em pares:
     * <td class="label"><span class="txt">RÓTULO</span></td>
     * <td class="data"><span class="txt">VALOR</span></td>
     */
    private BigDecimal extrairPorLabel(Document doc, String label) {
        // Estratégia 1: busca span.txt dentro de td.label
        Elements labels = doc.select("td.label span.txt");
        for (Element labelEl : labels) {
            if (labelEl.text().trim().equalsIgnoreCase(label)) {
                Element tdLabel = labelEl.parent();
                Element tdData  = tdLabel != null ? tdLabel.nextElementSibling() : null;
                if (tdData != null) {
                    String valor = tdData.text().trim();
                    BigDecimal parsed = parseToBigDecimal(valor);
                    if (parsed != null) return parsed;
                }
            }
        }

        // Estratégia 2: busca direta em td.label (sem span intermediário)
        Elements tdsLabel = doc.select("td.label");
        for (Element td : tdsLabel) {
            if (td.text().trim().equalsIgnoreCase(label)) {
                Element tdData = td.nextElementSibling();
                if (tdData != null) {
                    BigDecimal parsed = parseToBigDecimal(tdData.text().trim());
                    if (parsed != null) return parsed;
                }
            }
        }

        log.warn("[Fundamentus] Label '{}' não encontrado — pode ser ausente para este tipo de empresa.", label);
        return null;
    }

    /**
     * Converte string no formato brasileiro do Fundamentus para BigDecimal.
     * Trata: "12,34", "8,50%", "1.234,56", "-3,21%", "N/D", "-".
     */
    private BigDecimal parseToBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")
                || value.equalsIgnoreCase("N/D") || value.equalsIgnoreCase("N/A")) {
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
    public String getSourceName() { return "Fundamentus"; }

    @Override
    public int getPriority() { return 3; }
}