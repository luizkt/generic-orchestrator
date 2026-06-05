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
package com.orchestrator.service;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final WorkflowCacheService workflowCacheService;
    private final ContractValidationService contractValidationService;
    private final IntegrationExecutorFactory executorFactory;

    public FlowExecutionResult execute(String version, String flowId, Map<String, Object> payload) {
        String executionId = MDC.get("executionId");
        if (executionId == null) {
            executionId = UUID.randomUUID().toString();
            MDC.put("executionId", executionId);
        }
        MDC.put("flowId", flowId);
        MDC.put("version", version);

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setExecutionId(executionId);
        ctx.setFlowId(flowId);
        ctx.setStartedAt(LocalDateTime.now());
        ctx.setStatus(ExecutionStatus.RUNNING);
        ctx.setContract(payload != null ? payload : Map.of());

        log.info("Starting execution [{}] of flow: {} version: {}", executionId, flowId, version);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, version);
            contractValidationService.validate(def.getContract(), ctx.getContract());

            boolean hadError = false;
            for (IntegrationDefinition i :
                    def.getIntegrations().stream()
                            .sorted(Comparator.comparingInt(IntegrationDefinition::getOrder))
                            .toList()) {
                try {
                    Object result = executorFactory.get(i.getType()).execute(i, ctx);
                    ctx.putIntegrationResult(i.getId(), applyMapping(i, result));
                    log.info("Integration '{}' OK", i.getId());
                } catch (Exception e) {
                    if (i.isContinueOnError()) {
                        log.warn(
                                "Integration '{}' failed (continuing): {}",
                                i.getId(),
                                e.getMessage());
                        ctx.putIntegrationResult(i.getId(), Map.of("error", e.getMessage()));
                        hadError = true;
                    } else {
                        throw new IntegrationExecutionException(
                                "Required integration failed: " + i.getId(), e);
                    }
                }
            }

            if (def.getValidations() != null && !def.getValidations().isEmpty()) {
                for (IntegrationDefinition v :
                        def.getValidations().stream()
                                .sorted(Comparator.comparingInt(IntegrationDefinition::getOrder))
                                .toList()) {
                    try {
                        Object result = executorFactory.get(v.getType()).execute(v, ctx);
                        ctx.putValidationResult(v.getId(), applyMapping(v, result));
                        log.info("Validation '{}' OK", v.getId());
                    } catch (Exception e) {
                        if (v.isContinueOnError()) {
                            log.warn(
                                    "Validation '{}' failed (continuing): {}",
                                    v.getId(),
                                    e.getMessage());
                            ctx.putValidationResult(v.getId(), Map.of("error", e.getMessage()));
                            hadError = true;
                        } else {
                            throw new IntegrationExecutionException(
                                    "Required validation failed: " + v.getId(), e);
                        }
                    }
                }
            }

            ctx.setStatus(hadError ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS);
            ctx.setFinishedAt(LocalDateTime.now());

            return build(ctx, null);
        } catch (Exception e) {
            log.error("Error in execution [{}]: {}", ctx.getExecutionId(), e.getMessage(), e);
            ctx.setStatus(ExecutionStatus.FAILED);
            ctx.setErrorMessage(e.getMessage());
            ctx.setFinishedAt(LocalDateTime.now());
            return build(ctx, e.getMessage());
        }
    }

    private FlowExecutionResult build(FlowExecutionContext ctx, String error) {
        return FlowExecutionResult.builder()
                .executionId(ctx.getExecutionId())
                .flowId(ctx.getFlowId())
                .status(ctx.getStatus())
                .result(ctx.getIntegrations())
                .validations(ctx.getValidations())
                .errorMessage(error)
                .startedAt(ctx.getStartedAt())
                .finishedAt(ctx.getFinishedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Object applyMapping(IntegrationDefinition i, Object result) {
        var mapping =
                switch (i.getType()) {
                    case HTTP -> i.getHttp() != null ? i.getHttp().getResponseMapping() : null;
                    default -> null;
                };
        if (mapping == null || mapping.getSourceField() == null) return result;
        if (result instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(mapping.getSourceField());
            return Map.of(
                    mapping.getTargetField() != null ? mapping.getTargetField() : "value",
                    v != null ? v : "");
        }
        return result;
    }
}
