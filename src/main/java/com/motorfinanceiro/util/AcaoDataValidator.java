package com.motorfinanceiro.util;

import com.motorfinanceiro.dto.AcaoResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida a consistência dos dados fundamentalistas antes de enviá-los
 * à camada de IA.
 *
 * Problema que resolve: fontes de dados (Fundamentus, Yahoo, brapi)
 * às vezes retornam valores tecnicamente válidos mas financeiramente
 * absurdos — P/L negativo sem sinalização, P/VP de 300, liquidez de -5.
 * Sem essa validação, a IA recebe esses números e tenta calcular
 * Graham/Bazin com lixo, produzindo preços justos sem sentido.
 *
 * Esta classe NÃO corrige os dados — apenas sinaliza inconsistências
 * que são anexadas ao prompt da IA como avisos explícitos, para que
 * ela saiba quando NÃO aplicar uma fórmula ou quando alertar o usuário.
 */
public final class AcaoDataValidator {

    private static final Logger log = LoggerFactory.getLogger(AcaoDataValidator.class);

    // Limites de sanidade — fora disso, o dado é sinalizado como suspeito
    private static final BigDecimal PL_MAX_RAZOAVEL       = new BigDecimal("150");
    private static final BigDecimal PVP_MAX_RAZOAVEL      = new BigDecimal("50");
    private static final BigDecimal MARGEM_MIN_RAZOAVEL   = new BigDecimal("-100");
    private static final BigDecimal MARGEM_MAX_RAZOAVEL   = new BigDecimal("100");
    private static final BigDecimal DY_MAX_RAZOAVEL       = new BigDecimal("40");
    private static final BigDecimal LIQ_MIN_RAZOAVEL      = BigDecimal.ZERO;

    private AcaoDataValidator() {
        throw new UnsupportedOperationException("Classe utilitária — não instancie.");
    }

    /**
     * Resultado da validação: lista de avisos encontrados.
     * Lista vazia significa que todos os dados passaram nos critérios de sanidade.
     */
    public record ValidationResult(List<String> avisos) {
        public boolean temAvisos() { return !avisos.isEmpty(); }
    }

    /**
     * Executa todas as validações de sanidade sobre os dados de uma ação.
     *
     * @param acao Dados fundamentalistas a validar
     * @return Lista de avisos em linguagem natural, prontos para incluir no prompt da IA
     */
    public static ValidationResult validar(AcaoResponseDTO acao) {
        List<String> avisos = new ArrayList<>();

        // P/L negativo ou zero — empresa com prejuízo ou dado ausente mal tratado
        if (acao.pl() != null) {
            if (acao.pl().compareTo(BigDecimal.ZERO) <= 0) {
                avisos.add("P/L negativo ou zero (" + acao.pl()
                        + ") — empresa pode estar operando com prejuízo. "
                        + "NÃO calcule Preço Justo de Graham com este valor.");
            } else if (acao.pl().compareTo(PL_MAX_RAZOAVEL) > 0) {
                avisos.add("P/L extremamente elevado (" + acao.pl()
                        + ") — pode indicar lucro momentaneamente muito baixo "
                        + "(distorção contábil pontual) ou erro de dado da fonte.");
            }
        }

        // P/VP negativo ou absurdamente alto
        if (acao.pvp() != null) {
            if (acao.pvp().compareTo(BigDecimal.ZERO) <= 0) {
                avisos.add("P/VP negativo ou zero (" + acao.pvp()
                        + ") — patrimônio líquido pode ser negativo. Situação financeira de risco.");
            } else if (acao.pvp().compareTo(PVP_MAX_RAZOAVEL) > 0) {
                avisos.add("P/VP extremamente elevado (" + acao.pvp()
                        + ") — verifique se não há erro na fonte de dados antes de basear conclusões nisso.");
            }
        }

        // Margem líquida fora de faixa plausível
        if (acao.margemLiquida() != null) {
            if (acao.margemLiquida().compareTo(MARGEM_MIN_RAZOAVEL) < 0
                    || acao.margemLiquida().compareTo(MARGEM_MAX_RAZOAVEL) > 0) {
                avisos.add("Margem Líquida fora da faixa plausível (" + acao.margemLiquida()
                        + "%) — possível erro de escala na fonte de dados (verificar se não veio como decimal).");
            }
        }

        // Dividend Yield implausivelmente alto
        if (acao.dividendYield() != null
                && acao.dividendYield().compareTo(DY_MAX_RAZOAVEL) > 0) {
            avisos.add("Dividend Yield muito elevado (" + acao.dividendYield()
                    + "%) — pode ser dividendo extraordinário não recorrente, não a distribuição regular.");
        }

        // Liquidez corrente negativa (impossível na prática contábil)
        if (acao.liqCorrente() != null
                && acao.liqCorrente().compareTo(LIQ_MIN_RAZOAVEL) < 0) {
            avisos.add("Liquidez Corrente negativa (" + acao.liqCorrente()
                    + ") — valor contabilmente incomum, possível erro de dado.");
        }

        // Dados essenciais ausentes — a IA precisa saber que a análise está incompleta
        int camposAusentes = contarCamposNulos(acao);
        if (camposAusentes >= 6) {
            avisos.add("Apenas " + (12 - camposAusentes) + " de 12 indicadores fundamentalistas "
                    + "foram encontrados. A análise deve declarar explicitamente essa limitação "
                    + "e reduzir a confiança do veredito final.");
        }

        if (!avisos.isEmpty()) {
            log.warn("[AcaoDataValidator] {} avisos de sanidade para {}: {}",
                    avisos.size(), acao.ticker(), avisos);
        }

        return new ValidationResult(avisos);
    }

    private static int contarCamposNulos(AcaoResponseDTO a) {
        Object[] campos = {
                a.pl(), a.pvp(), a.dividendYield(), a.roe(), a.roic(),
                a.margemLiquida(), a.margemEbit(), a.evEbitda(),
                a.dividaBrutaPatrim(), a.crescRec5a(), a.liqCorrente()
        };
        int nulos = 0;
        for (Object c : campos) if (c == null) nulos++;
        return nulos;
    }

    /**
     * Formata os avisos como bloco de texto para anexar ao prompt da IA.
     * Retorna string vazia se não houver avisos.
     */
    public static String formatarParaPrompt(ValidationResult resultado) {
        if (!resultado.temAvisos()) return "";

        StringBuilder sb = new StringBuilder("\n\nAVISOS DE QUALIDADE DE DADOS (considere na análise):\n");
        for (String aviso : resultado.avisos()) {
            sb.append("- ").append(aviso).append("\n");
        }
        return sb.toString();
    }
}