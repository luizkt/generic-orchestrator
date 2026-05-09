package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class ValidationRule {
    private ValidationType tipo;
    private String valor;
    private Integer min;
    private Integer max;
    private String mensagem;
}
