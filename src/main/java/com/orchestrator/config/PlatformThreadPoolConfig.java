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
package com.orchestrator.config;

import com.orchestrator.config.properties.OrchestratorThreadPoolProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pool fixo de platform threads da v2, compartilhado por todas as requests.
 *
 * <p>Política de saturação: fila LIMITADA + {@link ThreadPoolExecutor.CallerRunsPolicy}. Com o pool
 * no máximo e a fila cheia, a tarefa roda na thread da request (degrada para execução serial, sem
 * erro e sem deadlock, pois tarefas do pool nunca submetem ao próprio pool). Cada ocorrência
 * incrementa {@code orchestrator.platform.pool.caller.runs}.
 *
 * <p>Métricas: {@code executor.*} (Micrometer) com tag {@code name=orchestrator-v2}.
 */
@Configuration
public class PlatformThreadPoolConfig {

    public static final String BEAN_NAME = "platformOrchestrationExecutor";
    public static final String METRIC_NAME = "orchestrator-v2";

    @Bean(name = BEAN_NAME, destroyMethod = "shutdown")
    public ThreadPoolExecutor platformOrchestrationExecutor(
            OrchestratorThreadPoolProperties props, MeterRegistry registry) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory =
                r -> {
                    Thread t = new Thread(r, props.getThreadNamePrefix() + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
        Counter callerRuns =
                Counter.builder("orchestrator.platform.pool.caller.runs")
                        .description("Tarefas executadas na thread da request por saturação")
                        .register(registry);
        ThreadPoolExecutor.CallerRunsPolicy delegate = new ThreadPoolExecutor.CallerRunsPolicy();

        ThreadPoolExecutor pool =
                new ThreadPoolExecutor(
                        props.getCoreSize(),
                        props.getMaxSize(),
                        props.getKeepAliveSeconds(),
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(props.getQueueCapacity()),
                        factory,
                        (task, executor) -> {
                            callerRuns.increment();
                            delegate.rejectedExecution(task, executor);
                        });
        new ExecutorServiceMetrics(pool, METRIC_NAME, Tags.empty()).bindTo(registry);
        return pool;
    }
}
