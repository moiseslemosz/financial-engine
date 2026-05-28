package com.motorfinanceiro.model;
 
/**
 * Tipos de investimento em renda fixa suportados pelo motor de cálculo.
 * O flag `isento` determina se há isenção de IR para pessoa física.
 */
public enum TipoInvestimento {
 
    CDB          ("Certificado de Depósito Bancário",         false),
    LCI          ("Letra de Crédito Imobiliário",              true),
    LCA          ("Letra de Crédito do Agronegócio",           true),
    CRI          ("Certificado de Recebíveis Imobiliários",    true),
    CRA          ("Certificado de Recebíveis do Agronegócio",  true),
    TESOURO_SELIC     ("Tesouro Selic",        false),
    TESOURO_IPCA      ("Tesouro IPCA+",        false),
    TESOURO_PREFIXADO ("Tesouro Prefixado",    false);
 
    private final String descricao;
    private final boolean isento;
 
    TipoInvestimento(String descricao, boolean isento) {
        this.descricao = descricao;
        this.isento    = isento;
    }
 
    public String  getDescricao() { return descricao; }
    public boolean isIsento()     { return isento;    }
}