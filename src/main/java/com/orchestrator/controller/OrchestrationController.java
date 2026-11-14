/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.orchestrator.controller;

import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.dto.OrchestrationResponse;
import com.orchestrator.metrics.ExecutionMetrics;
import com.orchestrator.service.OrchestrationService;
import com.orchestrator.service.OrchestrationV2Service;
import com.orchestrator.service.OrchestrationV3Service;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Execução")
@RestController
@RequiredArgsConstructor
public class OrchestrationController {

    private final OrchestrationService orchestrationService;
    private final OrchestrationV2Service orchestrationV2Service;
    private final OrchestrationV3Service orchestrationV3Service;
    private final ExecutionMetrics executionMetrics;

    /** v1 — execução sequencial (comportamento original). */
    @PostMapping("/api/v1/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> executeV1(
            @PathVariable String flowId,
            @PathVariable String version,
            @RequestBody Map<String, Object> payload) {
        return toResponse(
                executionMetrics.time(
                        "v1", () -> orchestrationService.execute(version, flowId, payload)));
    }

    /**
     * v2 — execução paralela de integrações com mesmo {@code order} em pool fixo compartilhado de
     * threads físicas (platform threads).
     */
    @PostMapping("/api/v2/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> executeV2(
            @PathVariable String flowId,
            @PathVariable String version,
            @RequestBody Map<String, Object> payload) {
        return toResponse(
                executionMetrics.time(
                        "v2", () -> orchestrationV2Service.execute(version, flowId, payload)));
    }

    /** v3 — execução paralela de integrações com mesmo {@code order} via Java Virtual Threads. */
    @PostMapping("/api/v3/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<OrchestrationResponse> executeV3(
            @PathVariable String flowId,
            @PathVariable String version,
            @RequestBody Map<String, Object> payload) {
        return toResponse(
                executionMetrics.time(
                        "v3", () -> orchestrationV3Service.execute(version, flowId, payload)));
    }

    private ResponseEntity<OrchestrationResponse> toResponse(FlowExecutionResult r) {
        return ResponseEntity.ok(
                OrchestrationResponse.builder()
                        .executionId(r.getExecutionId())
                        .flowId(r.getFlowId())
                        .status(r.getStatus())
                        .result(r.getResult())
                        .validations(r.getValidations())
                        .errorMessage(r.getErrorMessage())
                        .startedAt(r.getStartedAt())
                        .finishedAt(r.getFinishedAt())
                        .build());
    }
}
