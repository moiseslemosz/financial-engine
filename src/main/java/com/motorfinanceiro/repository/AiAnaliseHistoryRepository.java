package com.motorfinanceiro.repository;

import com.motorfinanceiro.model.AiAnaliseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório do histórico de análises de IA.
 * Segue o mesmo padrão de FiiHistoryRepository — query derivada, sem SQL manual.
 */
public interface AiAnaliseHistoryRepository extends JpaRepository<AiAnaliseHistory, Long> {

    /**
     * Retorna o histórico completo de um ticker, mais recente primeiro.
     */
    List<AiAnaliseHistory> findByTickerAndTipoAtivoOrderByAnalisadoEmDesc(
            String ticker, String tipoAtivo);

    /**
     * Retorna apenas a análise mais recente de um ticker — usada para
     * comparar o veredito atual com o anterior e detectar mudanças.
     */
    Optional<AiAnaliseHistory> findFirstByTickerAndTipoAtivoOrderByAnalisadoEmDesc(
            String ticker, String tipoAtivo);
}