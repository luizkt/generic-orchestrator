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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.orchestrator.config.PlatformThreadPoolConfig;
import com.orchestrator.config.properties.OrchestratorThreadPoolProperties;
import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/** Comportamento específico do pool físico (v2) e contraste com virtual threads (v3). */
@ExtendWith(MockitoExtension.class)
class PlatformPoolBehaviorTest {

    @Mock private WorkflowCacheService workflowCacheService;
    @Mock private ContractValidationService contractValidationService;
    @Mock private IntegrationExecutorFactory executorFactory;
    @Mock private IntegrationExecutor httpExecutor;

    private ThreadPoolExecutor pool;
    private SimpleMeterRegistry registry;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (pool != null) pool.shutdownNow();
    }

    private OrchestrationV2Service v2(int size, int queue) {
        OrchestratorThreadPoolProperties props = new OrchestratorThreadPoolProperties();
        props.setCoreSize(size);
        props.setMaxSize(size);
        props.setQueueCapacity(queue);
        registry = new SimpleMeterRegistry();
        pool = new PlatformThreadPoolConfig().platformOrchestrationExecutor(props, registry);
        return new OrchestrationV2Service(
                workflowCacheService, contractValidationService, executorFactory, pool);
    }

    private FlowDefinition flowWithParallelSteps(int n) {
        FlowContract contract = new FlowContract();
        contract.setFields(List.of());
        FlowDefinition flow = new FlowDefinition();
        flow.setFlowId("f");
        flow.setActive(true);
        flow.setContract(contract);
        List<IntegrationDefinition> steps = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            IntegrationDefinition i = new IntegrationDefinition();
            i.setId("s" + k);
            i.setOrder(1);
            i.setType(IntegrationType.HTTP);
            i.setHttp(new HttpIntegrationConfig());
            steps.add(i);
        }
        flow.setIntegrations(steps);
        return flow;
    }

    private void stub(FlowDefinition flow) {
        when(workflowCacheService.load("f", "1")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
    }

    @Test
    @DisplayName("v2 limita a concorrência ao tamanho do pool (pool=2, grupo de 4)")
    void concurrencyBoundedByPoolSize() {
        OrchestrationV2Service service = v2(2, 4);
        stub(flowWithParallelSteps(4));
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        when(httpExecutor.execute(any(), any()))
                .thenAnswer(
                        inv -> {
                            int c = current.incrementAndGet();
                            max.accumulateAndGet(c, Math::max);
                            Thread.sleep(50);
                            current.decrementAndGet();
                            return Map.of("ok", true);
                        });

        FlowExecutionResult r = service.execute("1", "f", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(max.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("v2 sob saturação (pool=1, fila=1): CallerRuns, sem deadlock nem erro")
    void saturationUsesCallerRunsWithoutDeadlock() {
        OrchestrationV2Service service = v2(1, 1);
        stub(flowWithParallelSteps(6));
        Set<String> threads = ConcurrentHashMap.newKeySet();
        when(httpExecutor.execute(any(), any()))
                .thenAnswer(
                        inv -> {
                            threads.add(Thread.currentThread().getName());
                            Thread.sleep(20);
                            return Map.of("ok", true);
                        });

        FlowExecutionResult r =
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                        java.time.Duration.ofSeconds(10),
                        () -> service.execute("1", "f", Map.of()));

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).hasSize(6);
        assertThat(registry.counter("orchestrator.platform.pool.caller.runs").count())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("v2 roda em platform threads orch-v2-*; v3 em virtual threads")
    void threadKinds() {
        OrchestrationV2Service service = v2(4, 4);
        stub(flowWithParallelSteps(3));
        Set<String> names = ConcurrentHashMap.newKeySet();
        Set<Boolean> virtuals = ConcurrentHashMap.newKeySet();
        when(httpExecutor.execute(any(), any()))
                .thenAnswer(
                        inv -> {
                            names.add(Thread.currentThread().getName());
                            virtuals.add(Thread.currentThread().isVirtual());
                            Thread.sleep(30);
                            return Map.of("ok", true);
                        });

        service.execute("1", "f", Map.of());
        assertThat(names).allMatch(n -> n.startsWith("orch-v2-"));
        assertThat(virtuals).containsExactly(false);

        virtuals.clear();
        new OrchestrationV3Service(workflowCacheService, contractValidationService, executorFactory)
                .execute("1", "f", Map.of());
        assertThat(virtuals).containsExactly(true);
    }

    @Test
    @DisplayName("MDC é propagado para a thread do pool e limpo ao devolvê-la")
    void mdcPropagatedAndCleaned() throws Exception {
        OrchestrationV2Service service = v2(2, 4);
        stub(flowWithParallelSteps(2));
        Set<String> seen = ConcurrentHashMap.newKeySet();
        when(httpExecutor.execute(any(), any()))
                .thenAnswer(
                        inv -> {
                            seen.add(String.valueOf(MDC.get("executionId")));
                            Thread.sleep(20);
                            return Map.of("ok", true);
                        });
        MDC.put("executionId", "exec-1");

        service.execute("1", "f", Map.of());

        assertThat(seen).containsExactly("exec-1");
        // thread do pool reaproveitada: MDC deve estar vazio
        Map<String, String> leaked = pool.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS);
        assertThat(leaked == null || leaked.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("CallerRuns preserva o MDC da thread da request")
    void callerRunsKeepsRequestMdc() {
        OrchestrationV2Service service = v2(1, 1);
        stub(flowWithParallelSteps(6));
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));
        MDC.put("executionId", "exec-2");

        service.execute("1", "f", Map.of());

        assertThat(MDC.get("executionId")).isEqualTo("exec-2");
    }
}
