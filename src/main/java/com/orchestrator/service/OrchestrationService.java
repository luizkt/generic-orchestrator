package com.orchestrator.service;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final WorkflowCacheService workflowCacheService;
    private final ContractValidationService contractValidationService;
    private final IntegrationExecutorFactory executorFactory;

    public FlowExecutionResult execute(String version, String flowId, Map<String, Object> payload) {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setExecutionId(UUID.randomUUID().toString());
        ctx.setFlowId(flowId);
        ctx.setStartedAt(LocalDateTime.now());
        ctx.setStatus(ExecutionStatus.RUNNING);
        ctx.setContract(payload != null ? payload : Map.of());

        log.info("Starting execution [{}] of flow: {} version: {}", ctx.getExecutionId(), flowId, version);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, version);
            contractValidationService.validate(def.getContract(), ctx.getContract());

            boolean hadError = false;
            for (IntegrationDefinition i : def.getIntegrations().stream()
                    .sorted(Comparator.comparingInt(IntegrationDefinition::getOrder)).toList()) {
                try {
                    Object result = executorFactory.get(i.getType()).execute(i, ctx);
                    ctx.putIntegrationResult(i.getId(), applyMapping(i, result));
                    log.info("Integration '{}' OK", i.getId());
                } catch (Exception e) {
                    if (i.isContinueOnError()) {
                        log.warn("Integration '{}' failed (continuing): {}", i.getId(), e.getMessage());
                        ctx.putIntegrationResult(i.getId(), Map.of("error", e.getMessage()));
                        hadError = true;
                    } else {
                        throw new IntegrationExecutionException(
                                "Required integration failed: " + i.getId(), e);
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
                .errorMessage(error)
                .startedAt(ctx.getStartedAt())
                .finishedAt(ctx.getFinishedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Object applyMapping(IntegrationDefinition i, Object result) {
        var mapping = switch (i.getType()) {
            case HTTP -> i.getHttp() != null ? i.getHttp().getResponseMapping() : null;
            default -> null;
        };
        if (mapping == null || mapping.getSourceField() == null) return result;
        if (result instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(mapping.getSourceField());
            return Map.of(mapping.getTargetField() != null ? mapping.getTargetField() : "value",
                    v != null ? v : "");
        }
        return result;
    }
}
