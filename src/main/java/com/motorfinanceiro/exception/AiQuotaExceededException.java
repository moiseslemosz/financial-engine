package com.motorfinanceiro.exception;
 
/**
 * Lançada quando todos os modelos de IA da cadeia de fallback
 * estão com cota esgotada ou indisponíveis.
 */
public class AiQuotaExceededException extends RuntimeException {
 
    public AiQuotaExceededException(String message) {
        super(message);
    }
 
    public AiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}