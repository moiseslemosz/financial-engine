package com.motorfinanceiro.dto;
 
import java.util.List;
 
public record CopomResponseDTO(
    String vies,
    String titulo,
    String resumo,
    String impactoRendaFixa,
    String impactoFiis,
    List<String> frasesChave,
    String perspectiva,
    // ── Roteamento de portfólio ──────────────────────────
    String rotacaoRendaFixa,
    String rotacaoFiis,
    String rotacaoAcao,
    // ── Meta ────────────────────────────────────────────
    String textoAnalisadoTrecho,
    boolean erroAi,
    String mensagemErro
) {}