package com.orchestrator.controller;

import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.dto.OrchestrationResponse;
import com.orchestrator.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Executes a workflow.
 *
 * REST-shape: POST creates a new resource in the sub-collection {@code executions}
 * of {@code /api/flows/{flowId}/versions/{version}}. After the REST refactor the
 * {@code orchestrate} verb was removed from the path.
 */
@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class OrchestrationController {

    private final OrchestrationService orchestrationService;

    @PostMapping("/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> execute(@PathVariable String flowId,
                                                         @PathVariable String version,
                                                         @RequestBody Map<String, Object> payload) {
        FlowExecutionResult r = orchestrationService.execute(version, flowId, payload);
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
