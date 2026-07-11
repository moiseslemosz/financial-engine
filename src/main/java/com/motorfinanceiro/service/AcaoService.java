package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orquestra a cadeia de fallback de dados de ações.
 *
 * Cadeia de prioridade:
 *   1. YahooFinanceDataSource  — 10 campos, não-oficial, pode bloquear em datacenter
 *   2. BrapiDataSource         — 6 campos, oficial, 1.000 req/mês gratuito
 *   3. FundamentusScraperStrategy — 12 campos, scraping, sem auth, sem SLA
 *
 * Comportamento do fallback:
 * - Erro de rede / HTTP 429 / timeout    → tenta próxima fonte
 * - HTTP 404 / ticker inválido           → propaga imediatamente (sem sentido tentar outra)
 * - Cotação null mesmo com HTTP 200      → tenta próxima fonte (dado inválido silencioso)
 * - Todas as fontes falharam             → lança ScraperException com histórico de erros
 */
@Service
public class AcaoService {

    private static final Logger log = LoggerFactory.getLogger(AcaoService.class);

    private final List<AcaoDataSource> sources;
    private final FundamentusScraperStrategy fundamentusFallback;

    public AcaoService(
            YahooFinanceDataSource yahooFinanceDataSource,
            BrapiDataSource brapiDataSource,
            FundamentusScraperStrategy fundamentusScraperStrategy) {

        // Ordem define a prioridade do fallback
        this.sources = List.of(yahooFinanceDataSource, brapiDataSource);
        this.fundamentusFallback = fundamentusScraperStrategy;
    }

    /**
     * Retorna dados fundamentalistas de uma ação com cache em memória.
     * Percorre a cadeia de fontes até obter dados válidos.
     *
     * @param ticker Código da ação na B3 (ex: VALE3, ITUB4, PETR4)
     * @return Dados fundamentalistas normalizados
     * @throws ScraperException se todas as fontes falharem
     */
    @Cacheable(value = "acao", key = "#ticker.toUpperCase()")
    public AcaoResponseDTO getAcaoData(String ticker) {
        String t = ticker.toUpperCase();
        log.info("[AcaoService] Cache MISS para {}. Iniciando cadeia de fontes...", t);

        StringBuilder errorHistory = new StringBuilder();

        // ── Tenta fontes API (Yahoo → brapi) ──────────────────────────────
        for (AcaoDataSource source : sources) {
            try {
                AcaoResponseDTO result = source.fetch(t);

                if (result.cotacao() == null) {
                    log.warn("[AcaoService] {} retornou dados mas cotação é null para {}. "
                            + "Tentando próxima fonte...", source.getSourceName(), t);
                    errorHistory.append(source.getSourceName()).append(": cotação null. ");
                    continue;
                }

                log.info("[AcaoService] Sucesso via {}: {} | Cotação: {} | P/L: {} | P/VP: {}",
                        source.getSourceName(), t, result.cotacao(), result.pl(), result.pvp());
                return result;

            } catch (ScraperException e) {
                if (isTickerNotFound(e)) {
                    // Ticker inválido — não adianta tentar outras fontes
                    log.error("[AcaoService] Ticker {} não encontrado em {}. Abortando cadeia.",
                            t, source.getSourceName());
                    throw e;
                }
                log.warn("[AcaoService] {} falhou para {}: {}. Tentando próxima fonte...",
                        source.getSourceName(), t, e.getMessage());
                errorHistory.append(source.getSourceName()).append(": ").append(e.getMessage()).append(". ");
            }
        }

        // ── Fallback final: Fundamentus (scraping) ─────────────────────────
        log.warn("[AcaoService] APIs falharam para {}. Acionando fallback Fundamentus (scraping)...", t);
        try {
            AcaoResponseDTO result = fundamentusFallback.fetch(t);
            log.info("[AcaoService] Sucesso via Fundamentus (fallback): {} | Cotação: {}",
                    t, result.cotacao());
            return result;
        } catch (ScraperException e) {
            errorHistory.append("Fundamentus: ").append(e.getMessage());
            log.error("[AcaoService] Todas as fontes falharam para {}. Histórico: {}", t, errorHistory);
            throw new ScraperException(
                    "Todas as fontes de dados falharam para " + t + ". " + errorHistory, e);
        }
    }

    /**
     * Remove o cache de um ticker específico.
     */
    @CacheEvict(value = "acao", key = "#ticker.toUpperCase()")
    public void evictCache(String ticker) {
        log.info("[AcaoService] Cache removido para ação: {}", ticker);
    }

    /**
     * Health Check assíncrono — verifica as três fontes a cada hora.
     * Usa WEGE3 como ticker de referência (industrial, todos os campos disponíveis).
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 90_000)
    public void healthCheck() {
        String testTicker = "WEGE3";
        log.info("[HealthCheck] Verificando fontes de ações com {}...", testTicker);

        for (AcaoDataSource source : sources) {
            checkSource(source, testTicker);
        }
        checkSource(fundamentusFallback, testTicker);

        log.info("[HealthCheck] Verificação de fontes de ações concluída.");
    }

    private void checkSource(AcaoDataSource source, String ticker) {
        try {
            AcaoResponseDTO result = source.fetch(ticker);
            if (result.cotacao() == null) {
                log.warn("[HealthCheck] ALERTA: {} retornou dados mas cotação é null. "
                        + "Verifique o mapeamento de campos.", source.getSourceName());
            } else {
                log.info("[HealthCheck] {} está saudável. {} | Cotação: {}",
                        source.getSourceName(), ticker, result.cotacao());
            }
        } catch (Exception e) {
            log.warn("[HealthCheck] ALERTA: {} falhou para {}. Motivo: {}",
                    source.getSourceName(), ticker, e.getMessage());
        }
    }

    /**
     * Detecta se o erro indica que o ticker não existe (não adianta tentar outra fonte).
     */
    private boolean isTickerNotFound(ScraperException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("404") || msg.contains("não encontrado") || msg.contains("not found");
    }
}