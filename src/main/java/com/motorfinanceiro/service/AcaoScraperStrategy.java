package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.AcaoResponseDTO;

/**
 * Interface do Strategy Pattern para scrapers de ações.
 * Segue o mesmo contrato que FiiScraperStrategy.
 */
public interface AcaoScraperStrategy {
    AcaoResponseDTO extractAcaoData(String ticker);
    String getSourceName();
}