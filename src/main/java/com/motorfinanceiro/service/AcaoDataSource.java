package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;

/**
 * Contrato unificado para todas as fontes de dados de ações.
 *
 * Cada implementação é responsável por:
 * 1. Fazer a requisição HTTP à sua fonte (API ou scraping)
 * 2. Normalizar os dados para o formato do AcaoResponseDTO
 * 3. Lançar ScraperException em caso de falha recuperável
 *
 * A cadeia de fallback é gerenciada pelo AcaoService — cada
 * DataSource não conhece a existência das outras fontes.
 */
public interface AcaoDataSource {

    /**
     * Busca e normaliza os dados fundamentalistas de uma ação.
     *
     * @param ticker Código da ação na B3 (ex: VALE3, ITUB4)
     * @return Dados normalizados — campos podem ser null se a fonte não os fornece
     * @throws com.motorfinanceiro.exception.ScraperException em caso de falha
     */
    AcaoResponseDTO fetch(String ticker);

    /**
     * Nome da fonte para logging e rastreabilidade no campo source do DTO.
     */
    String getSourceName();

    /**
     * Prioridade da fonte na cadeia de fallback (menor = maior prioridade).
     * Usado apenas para ordenação de logs — o AcaoService define a ordem real.
     */
    default int getPriority() { return 99; }
}