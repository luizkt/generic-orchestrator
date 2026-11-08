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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

/** Início e falha de execução, iguais em v1 e v2. */
final class ExecutionSupport {

    private ExecutionSupport() {}

    /** Garante o executionId no MDC, grava flowId e version e devolve o contexto em RUNNING. */
    static FlowExecutionContext begin(String version, String flowId, Map<String, Object> payload) {
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
        return ctx;
    }

    /** Marca a execução como FAILED e monta o resultado com a mensagem do erro. */
    static FlowExecutionResult fail(FlowExecutionContext ctx, Exception e) {
        ctx.setStatus(ExecutionStatus.FAILED);
        ctx.setErrorMessage(e.getMessage());
        ctx.setFinishedAt(LocalDateTime.now());
        return ResultMapper.build(ctx, e.getMessage());
    }
}
