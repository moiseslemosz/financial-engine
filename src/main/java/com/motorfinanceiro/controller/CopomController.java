package com.motorfinanceiro.controller;
 
import com.motorfinanceiro.dto.CopomRequestDTO;
import com.motorfinanceiro.dto.CopomResponseDTO;
import com.motorfinanceiro.dto.FiiAnaliseResponseDTO;
import com.motorfinanceiro.dto.FiiResponseDTO;
import com.motorfinanceiro.exception.ScraperException;
import com.motorfinanceiro.service.CopomAnalyzerService;
import com.motorfinanceiro.service.FiiAuditorService;
import com.motorfinanceiro.service.FiiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import java.time.LocalDateTime;
import java.util.Map;
 
/**
 * Passo 3.3 — Camada cognitiva exposta via REST.
 *
 * Rotas disponíveis:
 *
 *   POST /api/v1/copom/analisar
 *   → Tradutor do COPOM: converte texto técnico em análise acessível.
 *     Identifica viés (Hawkish/Dovish/Neutro) e impacto em renda fixa e FIIs.
 *
 *   GET /api/v1/fii/{ticker}/analise
 *   → Auditor de FIIs: busca dados numéricos e gera veredito qualitativo.
 *     Os números vêm do motor Java; a linguagem natural vem do Gemini.
 */
@RestController
@RequestMapping("/api/v1")
public class CopomController {
 
    private final CopomAnalyzerService copomAnalyzerService;
    private final FiiAuditorService    fiiAuditorService;
    private final FiiService           fiiService;
 
    public CopomController(
            CopomAnalyzerService copomAnalyzerService,
            FiiAuditorService    fiiAuditorService,
            FiiService           fiiService) {
        this.copomAnalyzerService = copomAnalyzerService;
        this.fiiAuditorService    = fiiAuditorService;
        this.fiiService           = fiiService;
    }
 
    // =========================================================================
    // TRADUTOR DO COPOM
    // =========================================================================
 
    /**
     * Analisa semanticamente um comunicado do COPOM.
     *
     * ─────────────────────────────────────────────────────────────
     * Exemplo de requisição:
     *
     * POST /api/v1/copom/analisar
     * Content-Type: application/json
     *
     * {
     *   "textoAta": "O Comitê de Política Monetária (Copom) decidiu,
     *                por unanimidade, elevar a taxa Selic em 0,25 ponto
     *                percentual para 13,25% a.a. O comitê avaliou que
     *                a inflação segue acima da meta e que o ambiente
     *                externo permanece desafiador..."
     * }
     * ─────────────────────────────────────────────────────────────
     *
     * Pode usar trechos da Ata oficial disponível em:
     * https://www.bcb.gov.br/publicacoes/notacopom
     */
    @PostMapping("/copom/analisar")
    public ResponseEntity<CopomResponseDTO> analisarCopom(
            @Valid @RequestBody CopomRequestDTO request) {
 
        CopomResponseDTO resultado = copomAnalyzerService.analisar(request.textoAta());
        return ResponseEntity.ok(resultado);
    }
 
    // =========================================================================
    // AUDITOR DE FIIS
    // =========================================================================
 
    /**
     * Busca os dados de um FII e retorna análise qualitativa da IA.
     *
     * O fluxo interno é:
     *   1. FiiService.getFiiData(ticker)  → dados numéricos (motor Java)
     *   2. FiiAuditorService.analisar()   → veredito qualitativo (Gemini)
     *   3. Combinação no FiiAnaliseResponseDTO
     *
     * Se o scraping falhar, retorna 503.
     * Se a IA falhar, retorna os dados numéricos com erroAi=true.
     *
     * ─────────────────────────────────────────────────────────────
     * Exemplo: GET /api/v1/fii/MXRF11/analise
     * ─────────────────────────────────────────────────────────────
     */
    @GetMapping("/fii/{ticker}/analise")
    public ResponseEntity<?> analisarFii(@PathVariable String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ticker inválido", "timestamp", LocalDateTime.now().toString()));
        }
 
        // Passo 1: dados numéricos via motor Java (scraper + cache)
        FiiResponseDTO dadosFii;
        try {
            dadosFii = fiiService.getFiiData(ticker.toUpperCase());
        } catch (ScraperException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Não foi possível obter dados do FII",
                            "message", e.getMessage(),
                            "ticker", ticker.toUpperCase(),
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }
 
        // Passo 2: análise qualitativa via Gemini (IA nunca calcula — só interpreta)
        FiiAnaliseResponseDTO analise = fiiAuditorService.analisar(dadosFii);
        return ResponseEntity.ok(analise);
    }
}