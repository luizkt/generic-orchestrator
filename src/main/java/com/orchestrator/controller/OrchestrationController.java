package com.orchestrator.controller;

import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.dto.OrchestrationResponse;
import com.orchestrator.service.OrchestrationService;
import com.orchestrator.service.OrchestrationV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrchestrationController {

    private final OrchestrationService orchestrationService;
    private final OrchestrationV2Service orchestrationV2Service;

    /** v1 — execução sequencial (comportamento original). */
    @PostMapping("/api/v1/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> executeV1(@PathVariable String flowId,
                                                           @PathVariable String version,
                                                           @RequestBody Map<String, Object> payload) {
        return toResponse(orchestrationService.execute(version, flowId, payload));
    }

    /** v2 — execução paralela de integrações com mesmo {@code order} via Java Virtual Threads. */
    @PostMapping("/api/v2/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> executeV2(@PathVariable String flowId,
                                                           @PathVariable String version,
                                                           @RequestBody Map<String, Object> payload) {
        return toResponse(orchestrationV2Service.execute(version, flowId, payload));
    }

    private ResponseEntity<OrchestrationResponse> toResponse(FlowExecutionResult r) {
        return ResponseEntity.ok(OrchestrationResponse.builder()
                .executionId(r.getExecutionId())
                .flowId(r.getFlowId())
                .status(r.getStatus())
                .result(r.getResult())
                .errorMessage(r.getErrorMessage())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .build());
    }
}
