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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ExecutionMetrics metrics = new ExecutionMetrics(registry);

    private FlowExecutionResult result(ExecutionStatus status) {
        return FlowExecutionResult.builder().status(status).build();
    }

    @Test
    @DisplayName("registra o timer com tags de versão e status do resultado")
    void recordsByVersionAndStatus() {
        metrics.time("v2", () -> result(ExecutionStatus.SUCCESS));
        metrics.time("v2", () -> result(ExecutionStatus.SUCCESS));
        metrics.time("v3", () -> result(ExecutionStatus.FAILED));

        assertThat(
                        registry.get(ExecutionMetrics.METRIC)
                                .tags("version", "v2", "status", "SUCCESS")
                                .timer()
                                .count())
                .isEqualTo(2);
        assertThat(
                        registry.get(ExecutionMetrics.METRIC)
                                .tags("version", "v3", "status", "FAILED")
                                .timer()
                                .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("exceção do serviço vira status ERROR e é propagada")
    void exceptionIsRecordedAsError() {
        assertThatThrownBy(
                        () ->
                                metrics.time(
                                        "v1",
                                        () -> {
                                            throw new IllegalStateException("boom");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(
                        registry.get(ExecutionMetrics.METRIC)
                                .tags("version", "v1", "status", "ERROR")
                                .timer()
                                .count())
                .isEqualTo(1);
    }
}
