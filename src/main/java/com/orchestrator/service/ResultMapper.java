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

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.IntegrationDefinition;
import java.util.Map;

/** Montagem do resultado e mapeamento de resposta, compartilhados pela execução v1 e v2. */
final class ResultMapper {

    private ResultMapper() {}

    static FlowExecutionResult build(FlowExecutionContext ctx, String error) {
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
    static Object applyMapping(IntegrationDefinition i, Object result) {
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
