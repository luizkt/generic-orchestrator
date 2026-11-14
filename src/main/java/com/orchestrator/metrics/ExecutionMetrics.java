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
package com.orchestrator.metrics;

import com.orchestrator.domain.execution.FlowExecutionResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Timer {@value #METRIC} com as tags {@code version} (v1, v2, v3) e {@code status} (SUCCESS,
 * PARTIAL_SUCCESS, FAILED ou ERROR). Permite comparar as três versões sem depender da URL no {@code
 * http_server_requests}.
 */
@Component
public class ExecutionMetrics {

    public static final String METRIC = "orchestrator.execution";

    private final MeterRegistry registry;

    public ExecutionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public FlowExecutionResult time(String version, Supplier<FlowExecutionResult> execution) {
        long start = System.nanoTime();
        String status = "ERROR";
        try {
            FlowExecutionResult result = execution.get();
            if (result != null && result.getStatus() != null) {
                status = result.getStatus().name();
            }
            return result;
        } finally {
            Timer.builder(METRIC)
                    .description("Duração da execução de um flow, por versão e status")
                    .tag("version", version)
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
