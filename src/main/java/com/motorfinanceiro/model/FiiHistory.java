package com.motorfinanceiro.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_fii_history")
public class FiiHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String ticker;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal dividendYield;

    @Column(precision = 5, scale = 2)
    private BigDecimal pvp;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private String source;

    // Construtor padrão exigido pelo Hibernate
    public FiiHistory() {}

    public FiiHistory(String ticker, BigDecimal currentPrice, BigDecimal dividendYield, BigDecimal pvp, String source) {
        this.ticker = ticker.toUpperCase();
        this.currentPrice = currentPrice;
        this.dividendYield = dividendYield;
        this.pvp = pvp;
        this.recordedAt = LocalDateTime.now();
        this.source = source;
    }

    // Getters
    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getDividendYield() { return dividendYield; }
    public BigDecimal getPvp() { return pvp; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public String getSource() { return source; }
}