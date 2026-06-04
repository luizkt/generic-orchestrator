package com.orchestrator.service;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestrationV2ServiceTest {

    @Mock private WorkflowCacheService workflowCacheService;
    @Mock private ContractValidationService contractValidationService;
    @Mock private IntegrationExecutorFactory executorFactory;
    @Mock private IntegrationExecutor httpExecutor;

    @InjectMocks private OrchestrationV2Service service;

    private FlowDefinition flow;

    @BeforeEach
    void setUp() {
        FlowContract contract = new FlowContract();
        contract.setFields(List.of());
        flow = new FlowDefinition();
        flow.setFlowId("test-flow");
        flow.setActive(true);
        flow.setContract(contract);
    }

    private IntegrationDefinition httpStep(String id, int order) {
        IntegrationDefinition i = new IntegrationDefinition();
        i.setId(id);
        i.setOrder(order);
        i.setType(IntegrationType.HTTP);
        i.setHttp(new HttpIntegrationConfig());
        return i;
    }

    @Test @DisplayName("Fluxo com única integração retorna SUCCESS")
    void singleIntegrationSuccess() {
        flow.setIntegrations(List.of(httpStep("step-1", 1)));
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKey("step-1");
    }

    @Test @DisplayName("Integrações com mesmo order executam em paralelo via virtual threads")
    void sameOrderRunsInParallel() throws Exception {
        IntegrationDefinition i1 = httpStep("step-a", 1);
        IntegrationDefinition i2 = httpStep("step-b", 1);
        flow.setIntegrations(List.of(i1, i2));

        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger current = new AtomicInteger(0);

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenAnswer(inv -> {
            int c = current.incrementAndGet();
            maxConcurrent.updateAndGet(m -> Math.max(m, c));
            bothStarted.countDown();
            bothStarted.await();
            current.decrementAndGet();
            return Map.of("ok", true);
        });

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKeys("step-a", "step-b");
        assertThat(maxConcurrent.get()).isEqualTo(2);
    }

    @Test @DisplayName("Grupos distintos executam sequencialmente")
    void differentOrderRunsSequentially() {
        IntegrationDefinition i1 = httpStep("step-1", 1);
        IntegrationDefinition i2 = httpStep("step-2", 2);
        flow.setIntegrations(List.of(i2, i1));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKeys("step-1", "step-2");
        verify(httpExecutor, times(2)).execute(any(), any());
    }

    @Test @DisplayName("PARTIAL_SUCCESS quando integração opcional falha no grupo paralelo")
    void optionalFailureInParallelGroupYieldsPartialSuccess() {
        IntegrationDefinition i1 = httpStep("step-a", 1);
        IntegrationDefinition i2 = httpStep("step-b", 1);
        i2.setContinueOnError(true);
        flow.setIntegrations(List.of(i1, i2));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(i1), any())).thenReturn(Map.of("ok", true));
        when(httpExecutor.execute(eq(i2), any())).thenThrow(new RuntimeException("timeout"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
        assertThat(r.getResult()).containsKey("step-a");
        assertThat(r.getResult()).containsKey("step-b");
        assertThat(r.getResult().get("step-b")).isEqualTo(Map.of("error", "timeout"));
    }

    @Test @DisplayName("FAILED quando integração obrigatória falha no grupo paralelo")
    void requiredFailureInParallelGroupYieldsFailed() {
        IntegrationDefinition i1 = httpStep("step-a", 1);
        IntegrationDefinition i2 = httpStep("step-b", 1);
        i1.setContinueOnError(false);
        i2.setContinueOnError(false);
        flow.setIntegrations(List.of(i1, i2));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(i1), any())).thenThrow(new RuntimeException("erro crítico"));
        when(httpExecutor.execute(eq(i2), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Required integration failed: step-a");
    }

    @Test @DisplayName("FAILED quando flow não encontrado")
    void flowNotFound() {
        when(workflowCacheService.load("test-flow", "9.9.9"))
                .thenThrow(new com.orchestrator.exception.FlowNotFoundException("Não encontrado"));

        FlowExecutionResult r = service.execute("9.9.9", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Não encontrado");
    }

    @Test @DisplayName("Executa validações após integrações e retorna resultados separados")
    void validationsRunAfterIntegrations() {
        IntegrationDefinition integration = httpStep("step-1", 1);
        IntegrationDefinition validation = httpStep("val-1", 1);
        flow.setIntegrations(List.of(integration));
        flow.setValidations(List.of(validation));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKey("step-1");
        assertThat(r.getValidations()).containsKey("val-1");
        verify(httpExecutor, times(2)).execute(any(), any());
    }

    @Test @DisplayName("PARTIAL_SUCCESS quando validação opcional falha")
    void optionalValidationFailureYieldsPartialSuccess() {
        IntegrationDefinition integration = httpStep("step-1", 1);
        IntegrationDefinition validation = httpStep("val-opt", 1);
        validation.setContinueOnError(true);
        flow.setIntegrations(List.of(integration));
        flow.setValidations(List.of(validation));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(integration), any())).thenReturn(Map.of("ok", true));
        when(httpExecutor.execute(eq(validation), any())).thenThrow(new RuntimeException("credit denied"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
        assertThat(r.getValidations().get("val-opt")).isEqualTo(Map.of("error", "credit denied"));
    }

    @Test @DisplayName("FAILED quando validação obrigatória falha")
    void requiredValidationFailureYieldsFailed() {
        IntegrationDefinition integration = httpStep("step-1", 1);
        IntegrationDefinition validation = httpStep("val-required", 1);
        validation.setContinueOnError(false);
        flow.setIntegrations(List.of(integration));
        flow.setValidations(List.of(validation));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(integration), any())).thenReturn(Map.of("ok", true));
        when(httpExecutor.execute(eq(validation), any())).thenThrow(new RuntimeException("failed"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Required validation failed: val-required");
    }

    @Test @DisplayName("Flow sem validações executa normalmente (backward compat)")
    void flowWithNoValidationsStillSucceeds() {
        flow.setIntegrations(List.of(httpStep("step-1", 1)));
        flow.setValidations(null);

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKey("step-1");
        assertThat(r.getValidations()).isEmpty();
    }
}
