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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Base comum das execuções paralelas por grupo de {@code order} (v2 e v3).
 *
 * <p>Integrações com o mesmo valor de {@code order} são disparadas simultaneamente; grupos
 * distintos rodam em sequência, e a fase de validações segue o mesmo padrão. Só o {@link
 * ExecutorService} muda entre as versões: as subclasses decidem como abri-lo e fechá-lo por grupo.
 *
 * <p>Semântica preservada: irmãos terminam antes de a falha ser relançada; grupos e validações
 * seguintes não rodam; {@code continueOnError} gera {@code PARTIAL_SUCCESS}; grupo de 1 passo roda
 * inline na thread da request.
 */
@Slf4j
public abstract class AbstractParallelOrchestrationService {

    private final WorkflowCacheService workflowCacheService;
    private final ContractValidationService contractValidationService;
    private final IntegrationExecutorFactory executorFactory;

    protected AbstractParallelOrchestrationService(
            WorkflowCacheService workflowCacheService,
            ContractValidationService contractValidationService,
            IntegrationExecutorFactory executorFactory) {
        this.workflowCacheService = workflowCacheService;
        this.contractValidationService = contractValidationService;
        this.executorFactory = executorFactory;
    }

    /** Tag usada nos logs, ex.: {@code v2}. */
    protected abstract String versionTag();

    /** Abre o executor para um grupo paralelo (novo por grupo ou compartilhado). */
    protected abstract ExecutorService openGroupExecutor();

    /** Libera o executor do grupo. Executor compartilhado NÃO pode ser fechado aqui. */
    protected abstract void closeGroupExecutor(ExecutorService executor);

    public FlowExecutionResult execute(String version, String flowId, Map<String, Object> payload) {
        FlowExecutionContext ctx = ExecutionSupport.begin(version, flowId, payload);
        String executionId = ctx.getExecutionId();

        log.info(
                "[{}] Starting execution [{}] of flow: {} version: {}",
                versionTag(),
                executionId,
                flowId,
                version);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, version);
            contractValidationService.validate(def.getContract(), ctx.getContract());

            AtomicBoolean hadError = new AtomicBoolean(false);

            executePhase(
                    def.getIntegrations(), ctx, hadError, ctx::putIntegrationResult, "integration");
            executePhase(
                    def.getValidations(), ctx, hadError, ctx::putValidationResult, "validation");

            ctx.setStatus(
                    hadError.get() ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS);
            ctx.setFinishedAt(LocalDateTime.now());
            return ResultMapper.build(ctx, null);

        } catch (Exception e) {
            log.error(
                    "[{}] Error in execution [{}]: {}",
                    versionTag(),
                    ctx.getExecutionId(),
                    e.getMessage(),
                    e);
            return ExecutionSupport.fail(ctx, e);
        }
    }

    private void executePhase(
            List<IntegrationDefinition> steps,
            FlowExecutionContext ctx,
            AtomicBoolean hadError,
            BiConsumer<String, Object> resultStore,
            String stepType) {
        if (steps == null || steps.isEmpty()) return;
        Map<Integer, List<IntegrationDefinition>> byOrder =
                steps.stream()
                        .collect(
                                Collectors.groupingBy(
                                        IntegrationDefinition::getOrder,
                                        TreeMap::new,
                                        Collectors.toList()));
        for (List<IntegrationDefinition> group : byOrder.values()) {
            if (group.size() == 1) {
                runStep(group.get(0), ctx, hadError, resultStore, stepType);
            } else {
                executeGroupParallel(group, ctx, hadError, resultStore, stepType);
            }
        }
    }

    private void executeGroupParallel(
            List<IntegrationDefinition> group,
            FlowExecutionContext ctx,
            AtomicBoolean hadError,
            BiConsumer<String, Object> resultStore,
            String stepType) {
        List<IntegrationExecutionException> requiredFailures = new ArrayList<>();
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        // Captura MDC e contexto OTel antes de submeter: a thread filha não os herda.
        Context otelCtx = Context.current();

        ExecutorService executor = openGroupExecutor();
        try {
            List<Future<?>> futures =
                    group.stream()
                            .<Future<?>>map(
                                    i ->
                                            executor.submit(
                                                    () ->
                                                            runWithContext(
                                                                    mdcSnapshot,
                                                                    otelCtx,
                                                                    () ->
                                                                            runStep(
                                                                                    i,
                                                                                    ctx,
                                                                                    hadError,
                                                                                    resultStore,
                                                                                    stepType))))
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
        } finally {
            closeGroupExecutor(executor);
        }

        if (!requiredFailures.isEmpty()) {
            throw requiredFailures.get(0);
        }
    }

    /**
     * Roda a tarefa com MDC/OTel do chamador e restaura o MDC que a thread tinha antes. Em thread
     * de pool reaproveitada isso evita vazar {@code executionId} entre requests; sob {@code
     * CallerRunsPolicy} (tarefa rodando na thread da request) preserva o MDC da própria request.
     */
    private void runWithContext(Map<String, String> mdcSnapshot, Context otelCtx, Runnable task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try (Scope ignored = otelCtx.makeCurrent()) {
            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
            task.run();
        } finally {
            if (previous != null) MDC.setContextMap(previous);
            else MDC.clear();
        }
    }

    private void runStep(
            IntegrationDefinition i,
            FlowExecutionContext ctx,
            AtomicBoolean hadError,
            BiConsumer<String, Object> resultStore,
            String stepType) {
        try {
            Object result = executorFactory.get(i.getType()).execute(i, ctx);
            resultStore.accept(i.getId(), ResultMapper.applyMapping(i, result));
            log.info("[{}] {} '{}' OK", versionTag(), stepType, i.getId());
        } catch (Exception e) {
            if (i.isContinueOnError()) {
                log.warn(
                        "[{}] {} '{}' failed (continuing): {}",
                        versionTag(),
                        stepType,
                        i.getId(),
                        e.getMessage());
                resultStore.accept(i.getId(), Map.of("error", e.getMessage()));
                hadError.set(true);
            } else {
                throw new IntegrationExecutionException(
                        "Required " + stepType + " failed: " + i.getId(), e);
            }
        }
    }
}
