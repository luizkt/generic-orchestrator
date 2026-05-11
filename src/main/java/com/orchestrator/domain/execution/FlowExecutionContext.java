package com.orchestrator.domain.execution;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
@Data
public class FlowExecutionContext {
    private String executionId;
    private String flowId;
    private Map<String, Object> contract = new HashMap<>();
    private Map<String, Object> integrations = new HashMap<>();
    private ExecutionStatus status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    public void putIntegrationResult(String id, Object r) { integrations.put(id, r); }
}
