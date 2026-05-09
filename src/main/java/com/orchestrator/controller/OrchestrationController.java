package com.orchestrator.controller;

import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.dto.OrchestrationResponse;
import com.orchestrator.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orchestrate")
@RequiredArgsConstructor
public class OrchestrationController {

    private final OrchestrationService orchestrationService;

    @PostMapping("/{flowId}")
    public ResponseEntity<OrchestrationResponse> orchestrate(@PathVariable String flowId,
                                                             @RequestBody Map<String, Object> payload) {
        FlowExecutionResult r = orchestrationService.execute(flowId, payload);
        return ResponseEntity.ok(OrchestrationResponse.builder()
                .executionId(r.getExecutionId())
                .flowId(r.getFlowId())
                .status(r.getStatus())
                .resultado(r.getResultado())
                .errorMessage(r.getErrorMessage())
                .iniciadoEm(r.getIniciadoEm())
                .finalizadoEm(r.getFinalizadoEm())
                .build());
    }
}
