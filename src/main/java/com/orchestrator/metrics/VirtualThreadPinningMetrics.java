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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import jdk.jfr.consumer.RecordingStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Conta virtual threads "presas" (pinning) via JFR: o evento {@code jdk.VirtualThreadPinned} é
 * emitido quando uma virtual thread bloqueia dentro de {@code synchronized} ou código nativo e
 * segura a carrier thread. Expõe {@code orchestrator_virtual_thread_pinned_total} e {@code
 * orchestrator_virtual_thread_pinned_duration_seconds}. Desliga com {@code
 * orchestrator.metrics.virtual-thread-pinning.enabled=false}.
 */
@Slf4j
@Component
public class VirtualThreadPinningMetrics {

    private final MeterRegistry registry;
    private final boolean enabled;
    private final long thresholdMs;
    private RecordingStream stream;

    public VirtualThreadPinningMetrics(
            MeterRegistry registry,
            @Value("${orchestrator.metrics.virtual-thread-pinning.enabled:true}") boolean enabled,
            @Value("${orchestrator.metrics.virtual-thread-pinning.threshold-ms:20}")
                    long thresholdMs) {
        this.registry = registry;
        this.enabled = enabled;
        this.thresholdMs = thresholdMs;
    }

    @PostConstruct
    void start() {
        if (!enabled) return;
        try {
            Counter pinned =
                    Counter.builder("orchestrator.virtual.thread.pinned")
                            .description("Virtual threads presas a uma carrier thread (JFR)")
                            .register(registry);
            Timer duration =
                    Timer.builder("orchestrator.virtual.thread.pinned.duration")
                            .description("Duração do pinning de virtual threads (JFR)")
                            .register(registry);
            stream = new RecordingStream();
            stream.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ofMillis(thresholdMs));
            stream.onEvent(
                    "jdk.VirtualThreadPinned",
                    event -> {
                        pinned.increment();
                        duration.record(event.getDuration());
                    });
            stream.setReuse(true);
            stream.startAsync();
            log.info("Métrica de pinning de virtual threads ativa (limiar {} ms)", thresholdMs);
        } catch (Throwable t) {
            log.warn("JFR indisponível; métrica de pinning desativada: {}", t.toString());
            close();
        }
    }

    @PreDestroy
    void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
