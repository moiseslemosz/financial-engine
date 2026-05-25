package com.motorfinanceiro.service;

import com.motorfinanceiro.dto.FiiResponseDTO;

public interface FiiScraperStrategy {
    
    /**
     * Extrai os dados financeiros de um ticker específico.
     * @param ticker Ex: "MXRF11"
     * @return DTO populado com os dados extraídos.
     */
    FiiResponseDTO extractFiiData(String ticker);
    
    /**
     * Identifica qual site esta estratégia está acessando.
     */
    String getSourceName();
}