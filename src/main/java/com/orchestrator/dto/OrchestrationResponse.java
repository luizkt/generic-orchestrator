package com.orchestrator.dto;
import com.orchestrator.domain.execution.ExecutionStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data @Builder
public class OrchestrationResponse {
    private String executionId;
    private String flowId;
    private ExecutionStatus status;
    private Map<String, Object> result;
    private Map<String, Object> validations;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
