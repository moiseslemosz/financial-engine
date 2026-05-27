package com.motorfinanceiro.repository;

import com.motorfinanceiro.model.FiiHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FiiHistoryRepository extends JpaRepository<FiiHistory, Long> {
    // Busca o histórico ordenado por data para permitir análises temporais no futuro
    List<FiiHistory> findByTickerOrderByRecordedAtDesc(String ticker);
}