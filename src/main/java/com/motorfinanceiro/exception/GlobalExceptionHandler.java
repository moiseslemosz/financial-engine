package com.motorfinanceiro.exception;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
 
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
 
/**
 * Passo 4.3 — Tratador global de exceções da API.
 *
 * Centraliza o tratamento de todos os erros em um único lugar,
 * garantindo que qualquer falha retorne sempre o mesmo formato JSON:
 *
 * {
 *   "timestamp": "...",
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "...",
 *   "path":      "..."  (opcional)
 * }
 *
 * Com isso, os controllers ficam limpos — sem try-catch individuais
 * para erros de infraestrutura. Cada controller foca apenas na
 * lógica de negócio feliz.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
 
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
 
    // =========================================================================
    // ERROS DE VALIDAÇÃO (@Valid / Jakarta Validation)
    // =========================================================================
 
    /**
     * Disparado quando o payload da requisição falha na validação do @Valid.
     *
     * Exemplo: POST /renda-fixa/comparar com prazoMeses = -1
     * Retorna 400 com todos os campos inválidos e suas mensagens.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
 
        Map<String, String> errosPorCampo = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        field -> field.getDefaultMessage() != null
                                ? field.getDefaultMessage()
                                : "Valor inválido",
                        (msg1, msg2) -> msg1  // em caso de campo duplicado, mantém o primeiro
                ));
 
        log.warn("[Validação] Erros nos campos: {}", errosPorCampo);
 
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErro(
                        HttpStatus.BAD_REQUEST,
                        "Dados inválidos na requisição",
                        Map.of("campos", errosPorCampo)
                ));
    }
 
    // =========================================================================
    // ERROS DE FORMATO / JSON MAL FORMADO
    // =========================================================================
 
    /**
     * Disparado quando o JSON enviado está mal formado ou contém tipos incorretos.
     *
     * Exemplo: enviar "prazoMeses": "vinte e quatro" em vez de um número.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonMalformado(
            HttpMessageNotReadableException ex) {
 
        log.warn("[JSON] Requisição com corpo inválido: {}", ex.getMessage());
 
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErro(
                        HttpStatus.BAD_REQUEST,
                        "JSON inválido ou tipo de dado incorreto. Verifique o corpo da requisição."
                ));
    }
 
    /**
     * Disparado quando um parâmetro de path ou query tem tipo incompatível.
     *
     * Exemplo: GET /fii/123 onde o ticker esperado é String (irrelevante neste caso,
     * mas útil se futuramente houver parâmetros numéricos).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoIncompativel(
            MethodArgumentTypeMismatchException ex) {
 
        String mensagem = String.format(
                "Parâmetro '%s' com valor '%s' é inválido.", ex.getName(), ex.getValue());
        log.warn("[Parâmetro] {}", mensagem);
 
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErro(HttpStatus.BAD_REQUEST, mensagem));
    }
 
    // =========================================================================
    // ERROS DE SCRAPING (fonte de dados indisponível)
    // =========================================================================
 
    /**
     * Disparado quando todas as fontes de dados (StatusInvest + FundsExplorer)
     * falharam ao tentar buscar dados de um FII.
     *
     * Retorna 503 Service Unavailable — o servidor está no ar,
     * mas a fonte externa está inacessível.
     */
    @ExceptionHandler(ScraperException.class)
    public ResponseEntity<Map<String, Object>> handleScraperException(ScraperException ex) {
        log.error("[Scraper] Falha nas fontes de dados: {}", ex.getMessage());
 
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErro(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Fonte de dados temporariamente indisponível. Tente novamente em instantes."
                ));
    }
 
    // =========================================================================
    // CATCH-ALL — qualquer erro não tratado acima
    // =========================================================================
 
    /**
     * Captura qualquer exceção não mapeada acima.
     * Retorna 500 sem expor detalhes internos ao cliente.
     *
     * O log registra a stack trace completa para debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleErroGenerico(Exception ex) {
        log.error("[Erro interno] Exceção não tratada: {}", ex.getMessage(), ex);
 
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErro(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno do servidor. Tente novamente em instantes."
                ));
    }
 
    // =========================================================================
    // BUILDER DO PAYLOAD DE ERRO
    // =========================================================================
 
    private Map<String, Object> buildErro(HttpStatus status, String mensagem) {
        return buildErro(status, mensagem, null);
    }
 
    /**
     * Constrói o mapa de erro no formato padrão da API.
     *
     * {
     *   "timestamp": "2026-05-29T20:00:00",
     *   "status":    400,
     *   "error":     "Bad Request",
     *   "message":   "...",
     *   "detalhes":  { ... }  ← presente apenas quando relevante
     * }
     */
    private Map<String, Object> buildErro(
            HttpStatus status,
            String mensagem,
            Map<String, Object> detalhes) {
 
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   mensagem);
 
        if (detalhes != null && !detalhes.isEmpty()) {
            body.putAll(detalhes);
        }
 
        return body;
    }
}