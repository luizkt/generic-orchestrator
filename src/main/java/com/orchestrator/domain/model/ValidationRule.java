package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class ValidationRule {
    private ValidationType type;
    private String value;
    private Integer min;
    private Integer max;
    private String message;
}
