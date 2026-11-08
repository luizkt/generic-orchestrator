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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final WorkflowCacheService workflowCacheService;
    private final ContractValidationService contractValidationService;
    private final IntegrationExecutorFactory executorFactory;

    public FlowExecutionResult execute(String version, String flowId, Map<String, Object> payload) {
        FlowExecutionContext ctx = ExecutionSupport.begin(version, flowId, payload);
        String executionId = ctx.getExecutionId();

        log.info("Starting execution [{}] of flow: {} version: {}", executionId, flowId, version);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, version);
            contractValidationService.validate(def.getContract(), ctx.getContract());

            boolean hadError =
                    runPhase(def.getIntegrations(), ctx, ctx::putIntegrationResult, "Integration");
            if (def.getValidations() != null && !def.getValidations().isEmpty()) {
                hadError |=
                        runPhase(def.getValidations(), ctx, ctx::putValidationResult, "Validation");
            }

            ctx.setStatus(hadError ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS);
            ctx.setFinishedAt(LocalDateTime.now());

            return ResultMapper.build(ctx, null);
        } catch (Exception e) {
            log.error("Error in execution [{}]: {}", ctx.getExecutionId(), e.getMessage(), e);
            return ExecutionSupport.fail(ctx, e);
        }
    }

    /** Roda os passos em ordem de {@code order}; devolve true se algum continueOnError falhou. */
    private boolean runPhase(
            List<IntegrationDefinition> steps,
            FlowExecutionContext ctx,
            BiConsumer<String, Object> resultStore,
            String label) {
        boolean hadError = false;
        for (IntegrationDefinition i :
                steps.stream()
                        .sorted(Comparator.comparingInt(IntegrationDefinition::getOrder))
                        .toList()) {
            try {
                Object result = executorFactory.get(i.getType()).execute(i, ctx);
                resultStore.accept(i.getId(), ResultMapper.applyMapping(i, result));
                log.info("{} '{}' OK", label, i.getId());
            } catch (Exception e) {
                if (i.isContinueOnError()) {
                    log.warn("{} '{}' failed (continuing): {}", label, i.getId(), e.getMessage());
                    resultStore.accept(i.getId(), Map.of("error", e.getMessage()));
                    hadError = true;
                } else {
                    throw new IntegrationExecutionException(
                            "Required " + label.toLowerCase() + " failed: " + i.getId(), e);
                }
            }
        }
        return hadError;
    }
}
