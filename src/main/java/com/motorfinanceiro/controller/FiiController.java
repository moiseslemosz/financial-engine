package com.motorfinanceiro.controller;
 
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.service.FiiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
 
/**
 * Controller responsável pelas rotas de dados, histórico e cache de FIIs.
 *
 * Rotas disponíveis:
 * GET  /api/v1/fii/{ticker}             → dados de um FII específico (Scraper + Cache)
 * GET  /api/v1/fii/{ticker}/history     → série histórica de cotações salva no banco
 * POST /api/v1/fii/{ticker}/cache/clear → invalidação manual de cache
 * GET  /api/v1/health                   → prova de vida da aplicação
 */
@RestController
@RequestMapping("/api/v1")
public class FiiController {
 
    private final FiiService fiiService;
 
    public FiiController(FiiService fiiService) {
        this.fiiService = fiiService;
    }
 
    /**
     * Rota principal de cotação de FIIs.
     */
    @GetMapping("/fii/{ticker}")
    public ResponseEntity<FiiResponseDTO> getFii(@PathVariable String ticker) {
    FiiResponseDTO data = fiiService.getFiiData(ticker.toUpperCase());
    return ResponseEntity.ok(data);
}

    /**
     * Rota de série histórica.
     */
    @GetMapping("/fii/{ticker}/history")
    public ResponseEntity<java.util.List<com.motorfinanceiro.model.FiiHistory>> getFiiHistory(@PathVariable String ticker) {
        var history = fiiService.getFiiHistory(ticker.toUpperCase());
        
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(history);
    }
 
    /**
     * Limpa o cache de um ticker específico.
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
     * Health Check da aplicação.
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