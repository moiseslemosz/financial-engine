package com.motorfinanceiro.dto;
 
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
 
/**
 * Payload de entrada para análise de comunicados do COPOM.
 *
 * Exemplo de uso:
 * POST /api/v1/copom/analisar
 * {
 *   "textoAta": "O Comitê de Política Monetária decidiu, por unanimidade,
 *                elevar a taxa Selic em 0,25 ponto percentual..."
 * }
 */
public record CopomRequestDTO(
 
    @NotBlank(message = "O texto da ata é obrigatório")
    @Size(min = 50, message = "Texto muito curto para ser analisado (mínimo 50 caracteres)")
    @Size(max = 15000, message = "Texto excede o limite de 15.000 caracteres")
    String textoAta
 
) {}