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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Executa o fluxo com paralelismo por grupo de {@code order}.
 *
 * Integrações com o mesmo valor de {@code order} são disparadas simultaneamente
 * via Java Virtual Threads (Project Loom). Grupos distintos são executados em
 * sequência — o grupo N+1 só começa após todos os itens do grupo N terminarem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationV2Service {

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

        log.info("[v2] Starting execution [{}] of flow: {} version: {}", ctx.getExecutionId(), flowId, version);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, version);
            contractValidationService.validate(def.getContract(), ctx.getContract());

            Map<Integer, List<IntegrationDefinition>> byOrder = def.getIntegrations().stream()
                    .collect(Collectors.groupingBy(IntegrationDefinition::getOrder, TreeMap::new, Collectors.toList()));

            AtomicBoolean hadError = new AtomicBoolean(false);

            for (List<IntegrationDefinition> group : byOrder.values()) {
                if (group.size() == 1) {
                    executeSingle(group.get(0), ctx, hadError);
                } else {
                    executeGroupParallel(group, ctx, hadError);
                }
            }

            ctx.setStatus(hadError.get() ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS);
            ctx.setFinishedAt(LocalDateTime.now());
            return build(ctx, null);

        } catch (Exception e) {
            log.error("[v2] Error in execution [{}]: {}", ctx.getExecutionId(), e.getMessage(), e);
            ctx.setStatus(ExecutionStatus.FAILED);
            ctx.setErrorMessage(e.getMessage());
            ctx.setFinishedAt(LocalDateTime.now());
            return build(ctx, e.getMessage());
        }
    }

    private void executeGroupParallel(List<IntegrationDefinition> group,
                                      FlowExecutionContext ctx,
                                      AtomicBoolean hadError) {
        List<IntegrationExecutionException> requiredFailures = new ArrayList<>();

        try (ExecutorService vte = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = group.stream()
                    .<Future<?>>map(i -> vte.submit(() -> runIntegration(i, ctx, hadError)))
                    .toList();

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof IntegrationExecutionException iee) {
                        requiredFailures.add(iee);
                    }
                }
            }
        }

        if (!requiredFailures.isEmpty()) {
            throw requiredFailures.get(0);
        }
    }

    private void executeSingle(IntegrationDefinition i, FlowExecutionContext ctx, AtomicBoolean hadError) {
        try {
            runIntegration(i, ctx, hadError);
        } catch (IntegrationExecutionException e) {
            throw e;
        }
    }

    private void runIntegration(IntegrationDefinition i, FlowExecutionContext ctx, AtomicBoolean hadError) {
        try {
            Object result = executorFactory.get(i.getType()).execute(i, ctx);
            ctx.putIntegrationResult(i.getId(), applyMapping(i, result));
            log.info("[v2] Integration '{}' OK", i.getId());
        } catch (Exception e) {
            if (i.isContinueOnError()) {
                log.warn("[v2] Integration '{}' failed (continuing): {}", i.getId(), e.getMessage());
                ctx.putIntegrationResult(i.getId(), Map.of("error", e.getMessage()));
                hadError.set(true);
            } else {
                throw new IntegrationExecutionException("Required integration failed: " + i.getId(), e);
            }
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
