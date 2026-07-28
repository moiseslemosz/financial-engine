package com.motorfinanceiro.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Histórico de análises de IA — registra o veredito emitido para cada
 * ativo ao longo do tempo, permitindo detectar mudanças de opinião
 * da IA entre consultas (ex: FII que era APROVADO virou REPROVADO).
 *
 * Uma linha por análise executada. Não sobrescreve — cresce ao longo
 * do tempo, formando um histórico auditável.
 */
@Entity
@Table(name = "tb_ai_analise_history", indexes = {
        @Index(name = "idx_ticker_tipo", columnList = "ticker, tipoAtivo")
})
public class AiAnaliseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String ticker;

    /** "FII" ou "ACAO" */
    @Column(nullable = false, length = 10)
    private String tipoAtivo;

    /** Veredicto qualitativo: OPORTUNIDADE/NEUTRO/AGUARDAR (FII) ou COMPRA/NEUTRO/EVITAR (Ação) */
    @Column(nullable = false, length = 20)
    private String veredicto;

    /** APROVADO | EM_OBSERVACAO | REPROVADO */
    @Column(name = "veredicto_status", length = 20)
    private String veredictoStatus;

    /** Cotação/preço no momento da análise, para referência histórica */
    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal precoNoMomento;

    /** Selic vigente no momento da análise — contexto macro para auditoria futura */
    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal selicNoMomento;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime analisadoEm;

    protected AiAnaliseHistory() {
        // Construtor exigido pelo Hibernate
    }

    public AiAnaliseHistory(String ticker, String tipoAtivo, String veredicto,
                             String veredictoStatus, java.math.BigDecimal precoNoMomento,
                             java.math.BigDecimal selicNoMomento) {
        this.ticker           = ticker;
        this.tipoAtivo        = tipoAtivo;
        this.veredicto        = veredicto;
        this.veredictoStatus  = veredictoStatus;
        this.precoNoMomento   = precoNoMomento;
        this.selicNoMomento   = selicNoMomento;
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getTipoAtivo() { return tipoAtivo; }
    public String getVeredicto() { return veredicto; }
    public String getVeredictoStatus() { return veredictoStatus; }
    public java.math.BigDecimal getPrecoNoMomento() { return precoNoMomento; }
    public java.math.BigDecimal getSelicNoMomento() { return selicNoMomento; }
    public LocalDateTime getAnalisadoEm() { return analisadoEm; }
}