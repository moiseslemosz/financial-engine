package com.motorfinanceiro.controller;

import com.motorfinanceiro.dto.AcaoAnaliseResponseDTO;
import com.motorfinanceiro.dto.AcaoResponseDTO;
import com.motorfinanceiro.service.AcaoAuditorService;
import com.motorfinanceiro.service.AcaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fase 6 — Rotas de análise fundamentalista de ações da B3.
 *
 * Rotas disponíveis:
 *
 *   GET /api/v1/acao/{ticker}
 *   → Dados fundamentalistas via Fundamentus (sem IA).
 *     Retorna cotação, P/L, P/VP, ROE, ROIC, margens, dívida, etc.
 *
 *   GET /api/v1/acao/{ticker}/analise
 *   → Dados fundamentalistas + análise qualitativa completa do Gemini.
 *     Inclui valuação (Graham, Bazin), qualidade, histórico e veredito.
 *
 *   POST /api/v1/acao/{ticker}/cache/clear
 *   → Remove o cache de um ticker específico.
 *
 * Exemplos:
 *   GET /api/v1/acao/VALE3
 *   GET /api/v1/acao/ITUB4/analise
 *   GET /api/v1/acao/PETR4/analise
 */
@RestController
@RequestMapping("/api/v1")
public class AcaoController {

    private final AcaoService acaoService;
    private final AcaoAuditorService acaoAuditorService;

    public AcaoController(AcaoService acaoService, AcaoAuditorService acaoAuditorService) {
        this.acaoService       = acaoService;
        this.acaoAuditorService = acaoAuditorService;
    }

    /**
     * Retorna os dados fundamentalistas de uma ação via Fundamentus.
     * Sem análise de IA — apenas os números brutos.
     *
     * Exemplo: GET /api/v1/acao/VALE3
     */
    @GetMapping("/acao/{ticker}")
    public ResponseEntity<AcaoResponseDTO> getAcao(@PathVariable String ticker) {
        AcaoResponseDTO data = acaoService.getAcaoData(ticker.toUpperCase());
        return ResponseEntity.ok(data);
    }

    /**
     * Retorna dados fundamentalistas + análise qualitativa do Gemini.
     * A IA calcula preço justo (Graham e Bazin), avalia qualidade e
     * emite veredito final (APROVADO / EM OBSERVAÇÃO / REPROVADO).
     *
     * Exemplo: GET /api/v1/acao/ITUB4/analise
     */
    @GetMapping("/acao/{ticker}/analise")
    public ResponseEntity<AcaoAnaliseResponseDTO> getAcaoAnalise(@PathVariable String ticker) {
        AcaoResponseDTO dados    = acaoService.getAcaoData(ticker.toUpperCase());
        AcaoAnaliseResponseDTO analise = acaoAuditorService.analisar(dados);
        return ResponseEntity.ok(analise);
    }

    /**
     * Remove o cache de um ticker específico.
     *
     * Exemplo: POST /api/v1/acao/VALE3/cache/clear
     */
    @PostMapping("/acao/{ticker}/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String ticker) {
        acaoService.evictCache(ticker.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "message",   "Cache removido para: " + ticker.toUpperCase(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}