package com.motorfinanceiro.service;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
 
/**
 * Orquestra o fluxo completo de extração de dados de FIIs:
 *
 *  1. Verifica o cache em memória (@Cacheable)
 *  2. Se não encontrado → tenta a fonte primária (StatusInvest)
 *  3. Se a primária falhar   → aciona o fallback (FundsExplorer)
 *  4. Se ambas falharem     → lança ScraperException para o controller tratar
 *
 * Além disso, executa um Health Check assíncrono a cada hora (@Scheduled)
 * para monitorar o contrato dos endpoints antes que o usuário perceba falhas.
 */
@Service
public class FiiService {
 
    private static final Logger log = LoggerFactory.getLogger(FiiService.class);
 
    private final FiiScraperStrategy primaryStrategy;
    private final FiiScraperStrategy fallbackStrategy;
 
    @Value("${fii.health-check.test-ticker:MXRF11}")
    private String healthCheckTicker;
 
    public FiiService(
            @Qualifier("primaryStrategy")  FiiScraperStrategy primaryStrategy,
            @Qualifier("fallbackStrategy") FiiScraperStrategy fallbackStrategy) {
        this.primaryStrategy  = primaryStrategy;
        this.fallbackStrategy = fallbackStrategy;
    }
 
    /**
     * Retorna os dados de um FII, com cache em memória.
     *
     * O cache é salvo com a chave do ticker (maiúsculo) e evita chamadas
     * desnecessárias às APIs externas durante o desenvolvimento, reduzindo
     * o risco de Rate Limit.
     *
     * TTL do cache: controlado por spring.cache.caffeine.spec em application.properties
     * Limpeza manual: disponível via endpoint POST /api/v1/fii/{ticker}/cache/clear
     */
    @Cacheable(value = "fii", key = "#ticker.toUpperCase()")
    public FiiResponseDTO getFiiData(String ticker) {
        log.info("[FiiService] Cache MISS para {}. Iniciando extração de dados...", ticker);
 
        // Tentativa 1: fonte primária
        try {
            FiiResponseDTO result = primaryStrategy.extractFiiData(ticker);
            log.info("[FiiService] Sucesso via {}. Ticker: {} | Preço: {}",
                    primaryStrategy.getSourceName(), ticker, result.currentPrice());
            return result;
 
        } catch (ScraperException primaryException) {
            log.warn("[FiiService] {} falhou para {}. Motivo: {}. Acionando fallback: {}...",
                    primaryStrategy.getSourceName(),
                    ticker,
                    primaryException.getMessage(),
                    fallbackStrategy.getSourceName());
        }
 
        // Tentativa 2: fallback automático
        try {
            FiiResponseDTO result = fallbackStrategy.extractFiiData(ticker);
            log.info("[FiiService] Sucesso via fallback ({}). Ticker: {} | Preço: {}",
                    fallbackStrategy.getSourceName(), ticker, result.currentPrice());
            return result;
 
        } catch (ScraperException fallbackException) {
            log.error("[FiiService] Todas as fontes falharam para {}. "
                    + "Verifique os logs acima para detalhes.", ticker);
            throw new ScraperException(
                    "Todas as fontes de dados falharam para o ticker: " + ticker,
                    fallbackException);
        }
    }
 
    /**
     * Remove o cache de um ticker específico.
     * Útil após detectar que os dados estão desatualizados.
     */
    @CacheEvict(value = "fii", key = "#ticker.toUpperCase()")
    public void evictCache(String ticker) {
        log.info("[FiiService] Cache removido para ticker: {}", ticker);
    }
 
    /**
     * Passo 1.7 — Health Check Assíncrono.
     *
     * Executa a cada hora em background para verificar se os endpoints externos
     * ainda estão respondendo com o contrato esperado (campos obrigatórios presentes).
     *
     * Detecta falhas ANTES que o usuário final perceba — os alertas aparecem nos logs
     * como WARNING para fácil monitoramento.
     *
     * fixedRate = 3_600_000ms = 1 hora
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void healthCheckEndpoints() {
        log.info("[HealthCheck] Iniciando verificação de saúde dos endpoints externos...");
 
        // Verifica fonte primária
        checkSource(primaryStrategy);
 
        // Verifica fonte secundária (fallback)
        checkSource(fallbackStrategy);
 
        log.info("[HealthCheck] Verificação concluída.");
    }
 
    private void checkSource(FiiScraperStrategy strategy) {
        try {
            FiiResponseDTO result = strategy.extractFiiData(healthCheckTicker);
 
            if (result.currentPrice() == null) {
                log.warn("[HealthCheck] ALERTA: {} retornou dados mas 'currentPrice' está nulo. "
                        + "O contrato do endpoint pode ter mudado.", strategy.getSourceName());
            } else {
                log.info("[HealthCheck] {} está saudável. {} | Preço: {}",
                        strategy.getSourceName(), healthCheckTicker, result.currentPrice());
            }
 
        } catch (Exception e) {
            log.warn("[HealthCheck] ALERTA: {} está falhando para {}. Motivo: {}",
                    strategy.getSourceName(), healthCheckTicker, e.getMessage());
        }
    }
}