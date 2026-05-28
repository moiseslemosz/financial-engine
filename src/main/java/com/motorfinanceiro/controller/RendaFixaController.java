package com.motorfinanceiro.controller;
 
import com.motorfinanceiro.dto.RendaFixaRequestDTO;
import com.motorfinanceiro.dto.RendaFixaResponseDTO;
import com.motorfinanceiro.service.RendaFixaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
/**
 * Passo 2.3 — Rota do motor determinístico de renda fixa.
 *
 * Expõe o endpoint POST /api/v1/renda-fixa/comparar que executa:
 *   - Juros compostos com e sem aportes mensais
 *   - Tabela regressiva de IR (CDB, Tesouro Direto)
 *   - Isenção de IR (LCI, LCA, CRI, CRA)
 *   - IOF regressivo (primeiros 30 dias corridos)
 *   - Ganho real descontando inflação (Equação de Fisher)
 *
 * O resultado é ordenado do maior para o menor montante líquido,
 * facilitando a tomada de decisão do investidor.
 */
@RestController
@RequestMapping("/api/v1")
public class RendaFixaController {
 
    private final RendaFixaService rendaFixaService;
 
    public RendaFixaController(RendaFixaService rendaFixaService) {
        this.rendaFixaService = rendaFixaService;
    }
 
    /**
     * Compara múltiplos investimentos de renda fixa com cálculo completo de impostos.
     *
     * ─────────────────────────────────────────────────────────────
     * Exemplo de requisição (Hoppscotch / curl):
     *
     * POST /api/v1/renda-fixa/comparar
     * Content-Type: application/json
     *
     * {
     *   "valorInicial":  10000.00,
     *   "aporteMensal":    500.00,
     *   "prazoMeses":        24,
     *   "inflacaoAnual":    4.50,
     *   "investimentos": [
     *     { "tipo": "CDB",  "taxaAnual": 12.50 },
     *     { "tipo": "LCI",  "taxaAnual":  9.00 },
     *     { "tipo": "LCA",  "taxaAnual":  9.50 },
     *     { "tipo": "TESOURO_SELIC", "taxaAnual": 10.75 }
     *   ]
     * }
     * ─────────────────────────────────────────────────────────────
     *
     * Tipos disponíveis:
     *   CDB | LCI | LCA | CRI | CRA
     *   TESOURO_SELIC | TESOURO_IPCA | TESOURO_PREFIXADO
     *
     * @param request Dados do investimento validados pelo Jakarta Validation
     * @return Comparativo completo com impostos, rentabilidades e ganho real
     */
    @PostMapping("/renda-fixa/comparar")
    public ResponseEntity<RendaFixaResponseDTO> comparar(
            @Valid @RequestBody RendaFixaRequestDTO request) {
 
        RendaFixaResponseDTO resultado = rendaFixaService.calcularComparativo(request);
        return ResponseEntity.ok(resultado);
    }
}