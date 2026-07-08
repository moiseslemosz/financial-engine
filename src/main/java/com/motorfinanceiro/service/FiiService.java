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
 
    private final FiiHistoryRepository fiiHistoryRepository;
    
    // Lista que conterá a nossa "corrente" de raspadores na ordem certa
    private final List<FiiScraperStrategy> scraperChain;
 
    @Value("${fii.health-check.test-ticker:MXRF11}")
    private String healthCheckTicker;
 
    public FiiService(
            @Qualifier("primaryStrategy")  FiiScraperStrategy primaryStrategy,
            @Qualifier("fallbackStrategy") FiiScraperStrategy fallbackStrategy,
            Investidor10ScraperStrategy investidor10Strategy,
            FundamentusFiiScraperStrategy fundamentusFiiStrategy,
            FiiHistoryRepository fiiHistoryRepository) {
            
        this.fiiHistoryRepository = fiiHistoryRepository;
        
        // Define a ordem de prioridade: 1. StatusInvest, 2. Fundamentus, , 3. FundsExplorer (Para FIIs tradicionais), 3. Investidor10 (Último recurso)
        this.scraperChain = List.of(primaryStrategy, fundamentusFiiStrategy, fallbackStrategy, investidor10Strategy);
    }
 
    @Cacheable(value = "fii", key = "#ticker.toUpperCase()")
    public FiiResponseDTO getFiiData(String ticker) {
        log.info("[FiiService] Cache MISS para {}. Iniciando extração de dados...", ticker);
        
        FiiResponseDTO partialResult = null;
        Exception lastException = null;

        // Percorre todos os scrapers configurados
        for (FiiScraperStrategy strategy : scraperChain) {
            try {
                FiiResponseDTO result = strategy.extractFiiData(ticker);
                
                // VALIDAÇÃO DE QUALIDADE (Se tem P/VP, é o dado perfeito!)
                if (result.pvp() != null) {
                    saveHistory(result); // Grava no banco de dados
                    log.info("[FiiService] Sucesso completo via {}. Ticker: {} | Preço: {}",
                            strategy.getSourceName(), ticker, result.currentPrice());
                    return result; 
                }
                
                // Se chegou aqui, o Scraper funcionou, mas o P/VP veio nulo
                if (partialResult == null) {
                    partialResult = result; // Guarda o primeiro resultado parcial (para não devolver a tela vazia)
                }
                
                log.warn("[{}] {} retornou sem P/VP. Tentando a próxima fonte...", strategy.getSourceName(), ticker);

            } catch (Exception e) {
                lastException = e;
                log.warn("[FiiService] {} falhou para {}. Motivo: {}", strategy.getSourceName(), ticker, e.getMessage());
            }
        }

        // Se o loop terminou e NENHUMA fonte tem o P/VP, devolvemos o resultado parcial
        if (partialResult != null) {
            saveHistory(partialResult); // Grava o parcial no banco de dados
            log.warn("[FiiService] Nenhuma fonte trouxe o P/VP para {}. Retornando dados parciais.", ticker);
            return partialResult;
        }

        // Se falhou tudo (ex: bloqueios de rede em todas as fontes)
        log.error("[FiiService] Todas as fontes falharam para {}.", ticker);
        throw new ScraperException("Todas as fontes de dados falharam para o ticker: " + ticker, lastException);
    }
    
    /**
     * Salva o registo na base de dados de forma silenciosa.
     * Se o banco falhar, o erro é logado mas a API continua a responder.
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
        // Verifica a saúde de todas as fontes da nossa corrente
        for(FiiScraperStrategy strategy : scraperChain) {
            checkSource(strategy);
        }
        log.info("[HealthCheck] Verificação concluída.");
    }

    public List<FiiHistory> getFiiHistory(String ticker) {
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