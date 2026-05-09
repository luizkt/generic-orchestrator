package com.orchestrator.domain.model;
import lombok.Data;
import java.util.List;
@Data
public class FieldDefinition {
    private String nome;
    private FieldType tipo;
    private boolean obrigatorio;
    private List<ValidationRule> validacoes;
    private FlowContract objeto;
}
