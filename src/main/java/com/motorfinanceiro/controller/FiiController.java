package com.motorfinanceiro.controller;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import com.motorfinanceiro.service.FiiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import java.time.LocalDateTime;
import java.util.Map;
 
/**
 * Controller responsável pelas rotas de FIIs e health check da aplicação.
 *
 * Rotas disponíveis:
 *   GET  /api/v1/fii/{ticker}             → dados de um FII específico
 *   POST /api/v1/fii/{ticker}/cache/clear → limpa o cache de um ticker
 *   GET  /api/v1/health                   → prova de vida da aplicação (para Koyeb/AWS)
 */
@RestController
@RequestMapping("/api/v1")
public class FiiController {
 
    private final FiiService fiiService;
 
    public FiiController(FiiService fiiService) {
        this.fiiService = fiiService;
    }
 
    /**
     * Passo 1.8 — Rota principal de FIIs.
     *
     * Retorna dados do fundo imobiliário: preço atual, Dividend Yield,
     * P/VP, data da última atualização e fonte dos dados.
     *
     * O ticker é case-insensitive: "mxrf11", "MXRF11" e "Mxrf11" retornam o mesmo resultado.
     *
     * Exemplo:
     *   GET /api/v1/fii/MXRF11
     *
     * Para testar via Hoppscotch (substituto online do Postman):
     *   1. Acesse https://hoppscotch.io
     *   2. Método: GET
     *   3. URL: http://localhost:8080/api/v1/fii/MXRF11
     *   4. Clique em Send
     */
    @GetMapping("/fii/{ticker}")
    public ResponseEntity<?> getFii(@PathVariable String ticker) {
        if (ticker == null || ticker.isBlank() || ticker.length() < 5 || ticker.length() > 8) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "error", "Ticker inválido",
                            "message", "O ticker deve ter entre 5 e 8 caracteres. Ex: MXRF11",
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }
 
        try {
            FiiResponseDTO data = fiiService.getFiiData(ticker.toUpperCase());
            return ResponseEntity.ok(data);
 
        } catch (ScraperException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Fonte de dados indisponível",
                            "message", e.getMessage(),
                            "ticker", ticker.toUpperCase(),
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }
    }
 
    /**
     * Limpa o cache de um ticker específico.
     * Útil quando os dados em cache estão desatualizados.
     *
     * Exemplo: POST /api/v1/fii/MXRF11/cache/clear
     */
    @PostMapping("/fii/{ticker}/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String ticker) {
        fiiService.evictCache(ticker.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "message", "Cache removido com sucesso para: " + ticker.toUpperCase(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
 
    /**
     * Passo 5.2 — Health Check da aplicação.
     *
     * Retorna 200 OK para indicar que a aplicação está no ar.
     * Utilizado pelo Koyeb e pela AWS para manter o contêiner ativo.
     *
     * Exemplo: GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "motor-financeiro",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}