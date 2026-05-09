package com.orchestrator.exception;
import lombok.Getter;
import java.util.List;
@Getter
public class ContractValidationException extends RuntimeException {
    private final List<String> errors;
    public ContractValidationException(String m, List<String> e) { super(m); this.errors = e; }
}
