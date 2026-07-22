package com.motorfinanceiro.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuração de cache com TTL via Caffeine.
 *
 * Substitui o cache simples (@EnableCaching padrão, sem expiração)
 * por um cache com expiração automática — essencial para dados de
 * mercado que mudam durante o pregão.
 *
 * TTLs diferentes por tipo de dado:
 * - "fii"  → 20 minutos (FIIs têm menor volatilidade intradiária)
 * - "acao" → 15 minutos (ações têm maior volatilidade)
 *
 * Após o TTL expirar, a próxima consulta aciona a cadeia de fallback
 * novamente automaticamente — sem necessidade de evict manual.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("fii", "acao");

        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());

        // Cache "fii" com TTL próprio (20 min) — sobrescreve o padrão acima
        manager.registerCustomCache("fii", Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(20, TimeUnit.MINUTES)
                .recordStats()
                .build());

        // Cache "acao" com TTL próprio (15 min)
        manager.registerCustomCache("acao", Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats()
                .build());

        return manager;
    }
}