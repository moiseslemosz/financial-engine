package com.motorfinanceiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Record para encapsular os dados de um Fundo Imobiliário.
 * Por ser um Record, todos os campos são implicitamente final (imutáveis) 
 * e getters/equals/hashcode são gerados automaticamente.
 */
public record FiiResponseDTO(
        String ticker,
        BigDecimal currentPrice,
        BigDecimal dividendYield,
        BigDecimal pvp,
        LocalDateTime lastUpdated,
        String source
) {
    // Podemos adicionar validações compactas diretamente no construtor do Record
    public FiiResponseDTO {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("O ticker não pode ser nulo ou vazio");
        }
        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O preço não pode ser negativo");
        }
    }
}