package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orquestra o fluxo de dados de ações:
 * cache em memória → Fundamentus (scraping).
 *
 * Segue o mesmo padrão do FiiService:
 * - @Cacheable evita rate limit durante desenvolvimento
 * - @Scheduled health check monitora a fonte em background
 */
@Service
public class AcaoService {

    private static final Logger log = LoggerFactory.getLogger(AcaoService.class);

    private final AcaoScraperStrategy scraperStrategy;

    public AcaoService(FundamentusScraperStrategy scraperStrategy) {
        this.scraperStrategy = scraperStrategy;
    }

    /**
     * Retorna dados fundamentalistas de uma ação com cache em memória.
     * Cache key = ticker em maiúsculas (VALE3, ITUB4, etc.)
     *
     * @param ticker Código da ação na B3 (ex: VALE3, ITUB4, PETR4)
     * @return Dados fundamentalistas da ação
     * @throws ScraperException se o Fundamentus não retornar dados válidos
     */
    @Cacheable(value = "acao", key = "#ticker.toUpperCase()")
    public AcaoResponseDTO getAcaoData(String ticker) {
        log.info("[AcaoService] Cache MISS para {}. Buscando no Fundamentus...", ticker);

        AcaoResponseDTO result = scraperStrategy.extractAcaoData(ticker);

        log.info("[AcaoService] Dados obtidos: {} | Cotação: {} | P/L: {} | P/VP: {} | ROE: {}%",
                result.ticker(), result.cotacao(), result.pl(), result.pvp(), result.roe());

        return result;
    }

    /**
     * Remove o cache de um ticker específico.
     * Útil após detectar que os dados estão desatualizados.
     */
    @CacheEvict(value = "acao", key = "#ticker.toUpperCase()")
    public void evictCache(String ticker) {
        log.info("[AcaoService] Cache removido para ação: {}", ticker);
    }

    /**
     * Health Check assíncrono — roda a cada hora para verificar se o
     * Fundamentus ainda está acessível e com estrutura HTML estável.
     *
     * Testa com VALE3 (alta liquidez, sempre disponível).
     * Alertas de estrutura HTML quebrada aparecem nos logs como WARNING.
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 90_000)
    public void healthCheckFundamentus() {
        String testTicker = "VALE3";
        log.info("[HealthCheck] Verificando Fundamentus com {}...", testTicker);

        try {
            AcaoResponseDTO result = scraperStrategy.extractAcaoData(testTicker);

            if (result.cotacao() == null) {
                log.warn("[HealthCheck] ALERTA: Fundamentus retornou dados mas 'cotacao' é nulo. "
                        + "Verifique se os seletores CSS ainda são válidos.");
            } else {
                log.info("[HealthCheck] Fundamentus está saudável. {} | Cotação: {}",
                        testTicker, result.cotacao());
            }
        } catch (Exception e) {
            log.warn("[HealthCheck] ALERTA: Fundamentus falhou para {}. Motivo: {}",
                    testTicker, e.getMessage());
        }
    }
}