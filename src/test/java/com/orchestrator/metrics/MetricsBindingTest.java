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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricsBindingTest {

    @Test
    @DisplayName("circuit breaker e retry aparecem no registry, inclusive os criados depois")
    void resilienceMetricsAreBound() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        CircuitBreakerRegistry cbs = CircuitBreakerRegistry.ofDefaults();
        RetryRegistry retries = RetryRegistry.ofDefaults();
        ResilienceMetricsConfig cfg = new ResilienceMetricsConfig();

        cfg.circuitBreakerMetrics(cbs).bindTo(meters);
        cfg.retryMetrics(retries).bindTo(meters);
        cbs.circuitBreaker("perf-1"); // criado depois do bind

        assertThat(meters.find("resilience4j.circuitbreaker.state").tag("name", "perf-1").gauges())
                .isNotEmpty();
    }

    @Test
    @DisplayName("pinning: desligado não abre JFR; ligado registra os medidores e fecha limpo")
    void pinningMetricsLifecycle() {
        SimpleMeterRegistry off = new SimpleMeterRegistry();
        new VirtualThreadPinningMetrics(off, false, 20).start();
        assertThat(off.find("orchestrator.virtual.thread.pinned").counter()).isNull();

        SimpleMeterRegistry on = new SimpleMeterRegistry();
        VirtualThreadPinningMetrics m = new VirtualThreadPinningMetrics(on, true, 20);
        m.start();
        assertThat(on.find("orchestrator.virtual.thread.pinned").counter()).isNotNull();
        assertThatCode(m::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pinning é contado quando a virtual thread bloqueia dentro de synchronized")
    void pinningIsCounted() throws Exception {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        VirtualThreadPinningMetrics m = new VirtualThreadPinningMetrics(meters, true, 5);
        m.start();
        Object lock = new Object();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            ex.submit(
                            () -> {
                                synchronized (lock) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            })
                    .get();
        }
        // o evento JFR chega de forma assíncrona
        long deadline = System.currentTimeMillis() + 10_000;
        double count = 0;
        while (System.currentTimeMillis() < deadline) {
            count = meters.get("orchestrator.virtual.thread.pinned").counter().count();
            if (count >= 1) break;
            Thread.sleep(200);
        }
        m.close();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
