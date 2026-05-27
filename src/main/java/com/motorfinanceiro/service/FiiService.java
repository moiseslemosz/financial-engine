package com.motorfinanceiro.service;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import com.motorfinanceiro.model.FiiHistory;
import com.motorfinanceiro.repository.FiiHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
 
@Service
public class FiiService {
 
    private static final Logger log = LoggerFactory.getLogger(FiiService.class);
 
    private final FiiScraperStrategy primaryStrategy;
    private final FiiScraperStrategy fallbackStrategy;
    private final FiiHistoryRepository fiiHistoryRepository; // INJEÇÃO DO REPOSITÓRIO
 
    @Value("${fii.health-check.test-ticker:MXRF11}")
    private String healthCheckTicker;
 
    public FiiService(
            @Qualifier("primaryStrategy")  FiiScraperStrategy primaryStrategy,
            @Qualifier("fallbackStrategy") FiiScraperStrategy fallbackStrategy,
            FiiHistoryRepository fiiHistoryRepository) {
        this.primaryStrategy  = primaryStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.fiiHistoryRepository = fiiHistoryRepository;
    }
 
    @Cacheable(value = "fii", key = "#ticker.toUpperCase()")
    public FiiResponseDTO getFiiData(String ticker) {
        log.info("[FiiService] Cache MISS para {}. Iniciando extração de dados...", ticker);
 
        // Tentativa 1: fonte primária
        try {
            FiiResponseDTO result = primaryStrategy.extractFiiData(ticker);
            saveHistory(result); // GRAVA NO BANCO
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
            saveHistory(result); // GRAVA NO BANCO
            log.info("[FiiService] Sucesso via fallback ({}). Ticker: {} | Preço: {}",
                    fallbackStrategy.getSourceName(), ticker, result.currentPrice());
            return result;
 
        } catch (ScraperException fallbackException) {
            log.error("[FiiService] Todas as fontes falharam para {}. Verifique os logs acima para detalhes.", ticker);
            throw new ScraperException("Todas as fontes de dados falharam para o ticker: " + ticker, fallbackException);
        }
    }
    
    /**
     * Salva o registro no banco de dados de forma silenciosa.
     * Se o banco falhar, o erro é logado mas a API continua respondendo.
     */
    private void saveHistory(FiiResponseDTO result) {
        try {
            FiiHistory history = new FiiHistory(
                    result.ticker(),
                    result.currentPrice(),
                    result.dividendYield(),
                    result.pvp(),
                    result.source()
            );
            fiiHistoryRepository.save(history);
            log.debug("[FiiService] Histórico gravado no PostgreSQL para o ticker: {}", result.ticker());
        } catch (Exception e) {
            log.error("[FiiService] Falha ao gravar histórico no banco de dados: {}", e.getMessage());
        }
    }
 
    @CacheEvict(value = "fii", key = "#ticker.toUpperCase()")
    public void evictCache(String ticker) {
        log.info("[FiiService] Cache removido para ticker: {}", ticker);
    }
 
    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void healthCheckEndpoints() {
        log.info("[HealthCheck] Iniciando verificação de saúde dos endpoints externos...");
        checkSource(primaryStrategy);
        checkSource(fallbackStrategy);
        log.info("[HealthCheck] Verificação concluída.");
    }

    public java.util.List<FiiHistory> getFiiHistory(String ticker) {
        log.info("[FiiService] Buscando histórico no banco para o ticker: {}", ticker);
        return fiiHistoryRepository.findByTickerOrderByRecordedAtDesc(ticker.toUpperCase());
    }
 
    private void checkSource(FiiScraperStrategy strategy) {
        try {
            FiiResponseDTO result = strategy.extractFiiData(healthCheckTicker);
            if (result.currentPrice() == null) {
                log.warn("[HealthCheck] ALERTA: {} retornou dados mas 'currentPrice' está nulo.", strategy.getSourceName());
            } else {
                log.info("[HealthCheck] {} está saudável. {} | Preço: {}", strategy.getSourceName(), healthCheckTicker, result.currentPrice());
            }
        } catch (Exception e) {
            log.warn("[HealthCheck] ALERTA: {} está falhando para {}. Motivo: {}", strategy.getSourceName(), healthCheckTicker, e.getMessage());
        }
    }
}