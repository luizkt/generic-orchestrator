package com.orchestrator.domain.model;
import lombok.Data;
import java.util.List;
@Data
public class FieldDefinition {
    private String name;
    private FieldType type;
    private boolean required;
    private List<ValidationRule> validations;
    private FlowContract object;
}
