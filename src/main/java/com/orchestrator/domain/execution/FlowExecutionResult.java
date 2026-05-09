package com.orchestrator.domain.execution;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data @Builder
public class FlowExecutionResult {
    private String executionId;
    private String flowId;
    private ExecutionStatus status;
    private Map<String, Object> resultado;
    private String errorMessage;
    private LocalDateTime iniciadoEm;
    private LocalDateTime finalizadoEm;
}
