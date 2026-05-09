package com.orchestrator.domain.execution;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
@Data
public class FlowExecutionContext {
    private String executionId;
    private String flowId;
    private Map<String, Object> contrato = new HashMap<>();
    private Map<String, Object> integracoes = new HashMap<>();
    private ExecutionStatus status;
    private String errorMessage;
    private LocalDateTime iniciadoEm;
    private LocalDateTime finalizadoEm;
    public void putIntegrationResult(String id, Object r) { integracoes.put(id, r); }
}
