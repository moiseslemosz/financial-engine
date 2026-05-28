package com.motorfinanceiro.service;
 
import com.motorfinanceiro.dto.*;
import com.motorfinanceiro.model.TipoInvestimento;
import com.motorfinanceiro.util.Calculations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
 
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
 
/**
 * Orquestra o comparativo de renda fixa delegando todos os cálculos
 * à classe {@link Calculations}.
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  GARANTIA: nenhum número exibido ao usuário passa pela IA.      ║
 * ║  Este service nunca chama o Spring AI — apenas Calculations.    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Service
public class RendaFixaService {
 
    private static final Logger log = LoggerFactory.getLogger(RendaFixaService.class);
 
    /**
     * Calcula e compara múltiplos investimentos de renda fixa.
     *
     * Fluxo geral:
     * 1. Calcula o total de capital desembolsado no período
     * 2. Para cada investimento: executa o pipeline completo de cálculo
     * 3. Ordena os resultados do maior para o menor montante líquido
     *
     * @param request Dados do investimento e lista de tipos a comparar
     * @return Comparativo completo ordenado pelo melhor retorno líquido
     */
    public RendaFixaResponseDTO calcularComparativo(RendaFixaRequestDTO request) {
        log.info("[RendaFixa] Iniciando comparativo: {} investimentos | prazo={}m | " +
                 "valorInicial={} | aporte={}",
                request.investimentos().size(),
                request.prazoMeses(),
                request.valorInicial(),
                request.aporteMensal());
 
        BigDecimal totalInvestido = calcularTotalInvestido(request);
        int diasCorridos          = Calculations.mesesParaDias(request.prazoMeses());
 
        List<ResultadoCalculoDTO> comparativo = request.investimentos().stream()
                .map(item -> calcularResultado(item, request, totalInvestido, diasCorridos))
                .sorted(Comparator.comparing(ResultadoCalculoDTO::montanteLiquido).reversed())
                .toList();
 
        log.info("[RendaFixa] Comparativo concluído. Melhor opção: {} | Montante líquido: {}",
                comparativo.isEmpty() ? "N/A" : comparativo.get(0).tipo(),
                comparativo.isEmpty() ? "N/A" : comparativo.get(0).montanteLiquido());
 
        return new RendaFixaResponseDTO(
                request.valorInicial(),
                request.aporteMensal(),
                request.prazoMeses(),
                totalInvestido,
                comparativo
        );
    }
 
    // =========================================================================
    // PIPELINE DE CÁLCULO POR INVESTIMENTO
    // =========================================================================
 
    /**
     * Executa o pipeline completo de cálculo para um único investimento.
     *
     * Pipeline:
     *   [1] taxa anual → taxa mensal equivalente (regime composto)
     *   [2] montante bruto (juros compostos com/sem aportes mensais)
     *   [3] lucro bruto  = montante bruto − total investido
     *   [4] IOF          = lucro bruto × alíquota IOF (0 se prazo > 30 dias)
     *   [5] IR           = lucro bruto × alíquota IR  (0 se isento)
     *   [6] lucro líquido = lucro bruto − IOF − IR
     *   [7] montante líquido = total investido + lucro líquido
     *   [8] rentabilidades bruta e líquida em %
     *   [9] ganho real (Equação de Fisher)
     */
    private ResultadoCalculoDTO calcularResultado(
            InvestimentoItemDTO item,
            RendaFixaRequestDTO request,
            BigDecimal totalInvestido,
            int diasCorridos) {
 
        TipoInvestimento tipo = item.tipo();
 
        // [1] Taxa mensal equivalente
        BigDecimal taxaMensal = Calculations.taxaMensalEquivalente(item.taxaAnual());
 
        // [2] Montante bruto — juros compostos com aportes mensais
        BigDecimal montanteBruto = Calculations.calcularMontante(
                request.valorInicial(),
                request.aporteMensal(),
                taxaMensal,
                request.prazoMeses()
        );
 
        // [3] Lucro bruto
        BigDecimal lucroBruto = montanteBruto.subtract(totalInvestido);
 
        // [4] IOF — aplica a todos os tipos nos primeiros 30 dias corridos
        BigDecimal impostoIOF = Calculations.calcularIOF(lucroBruto, diasCorridos);
 
        // [5] IR — somente para investimentos não isentos (CDB, Tesouro)
        BigDecimal impostoIR          = BigDecimal.ZERO;
        BigDecimal aliquotaIRPercent  = BigDecimal.ZERO;
 
        if (!tipo.isIsento()) {
            impostoIR = Calculations.calcularIR(lucroBruto, diasCorridos);
            aliquotaIRPercent = Calculations.aliquotaIR(diasCorridos)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, Calculations.ROUNDING);
        }
 
        // [6] Lucro líquido
        BigDecimal lucroLiquido = lucroBruto
                .subtract(impostoIR)
                .subtract(impostoIOF);
 
        // [7] Montante líquido
        BigDecimal montanteLiquido = totalInvestido.add(lucroLiquido);
 
        // [8] Rentabilidades
        BigDecimal rentabilidadeBruta   = Calculations.calcularRentabilidade(lucroBruto,  totalInvestido);
        BigDecimal rentabilidadeLiquida = Calculations.calcularRentabilidade(lucroLiquido, totalInvestido);
 
        // [9] Ganho real (retorno líquido descontada a inflação do período)
        BigDecimal ganhoReal = Calculations.calcularGanhoReal(
                rentabilidadeLiquida,
                request.inflacaoAnual(),
                request.prazoMeses()
        );
 
        log.debug("[RendaFixa] {} {}% a.a. → bruto={} | líquido={} | IR={} | IOF={} | ganhoReal={}%",
                tipo.name(), item.taxaAnual(),
                montanteBruto, montanteLiquido,
                impostoIR, impostoIOF, ganhoReal);
 
        return new ResultadoCalculoDTO(
                tipo.name(),
                tipo.getDescricao(),
                item.taxaAnual(),
                tipo.isIsento(),
                aliquotaIRPercent,
                montanteBruto,
                montanteLiquido,
                lucroBruto,
                lucroLiquido,
                impostoIR,
                impostoIOF,
                rentabilidadeBruta,
                rentabilidadeLiquida,
                ganhoReal
        );
    }
 
    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================
 
    /**
     * Total de capital efetivamente desembolsado pelo investidor no período.
     *
     * total = valorInicial + (aporteMensal × prazoMeses)
     *
     * Este é o custo real do investimento, usado como base para calcular
     * rentabilidade e ganho real.
     */
    private BigDecimal calcularTotalInvestido(RendaFixaRequestDTO request) {
        BigDecimal totalAportes = request.aporteMensal()
                .multiply(BigDecimal.valueOf(request.prazoMeses()));
        return request.valorInicial()
                .add(totalAportes)
                .setScale(Calculations.SCALE_MONEY, Calculations.ROUNDING);
    }
}