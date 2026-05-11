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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestrationServiceTest {

    @Mock private WorkflowCacheService workflowCacheService;
    @Mock private ContractValidationService contractValidationService;
    @Mock private IntegrationExecutorFactory executorFactory;
    @Mock private IntegrationExecutor httpExecutor;

    @InjectMocks private OrchestrationService service;

    private FlowDefinition flow;
    private IntegrationDefinition integration;

    @BeforeEach
    void setUp() {
        FlowContract contract = new FlowContract();
        contract.setFields(List.of());
        integration = new IntegrationDefinition();
        integration.setId("step-1");
        integration.setOrder(1);
        integration.setType(IntegrationType.HTTP);
        integration.setHttp(new HttpIntegrationConfig());
        flow = new FlowDefinition();
        flow.setFlowId("test-flow");
        flow.setActive(true);
        flow.setContract(contract);
        flow.setIntegrations(List.of(integration));
    }

    @Test @DisplayName("Executa fluxo com sucesso (load via WorkflowCacheService)")
    void deveExecutarFluxoComSucesso() {
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult result = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getResult()).containsKey("step-1");
        verify(workflowCacheService).load("test-flow", "1.0.0");
        verify(contractValidationService).validate(any(), any());
    }

    @Test @DisplayName("Retorna FAILED quando integração obrigatória falha")
    void deveFalharQuandoIntegracaoObrigatoriaFalha() {
        integration.setContinueOnError(false);
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test @DisplayName("Retorna PARTIAL_SUCCESS quando integração não-obrigatória falha")
    void devePartialSuccessQuandoOpcionalFalha() {
        integration.setContinueOnError(true);
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
    }

    @Test @DisplayName("Executa integrações na ordem definida")
    void deveExecutarEmOrdem() {
        IntegrationDefinition i2 = new IntegrationDefinition();
        i2.setId("step-2"); i2.setOrder(2); i2.setType(IntegrationType.HTTP);
        i2.setHttp(new HttpIntegrationConfig());
        IntegrationDefinition i0 = new IntegrationDefinition();
        i0.setId("step-0"); i0.setOrder(0); i0.setType(IntegrationType.HTTP);
        i0.setHttp(new HttpIntegrationConfig());
        flow.setIntegrations(List.of(i2, integration, i0));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKeys("step-0", "step-1", "step-2");
    }

    @Test @DisplayName("Retorna FAILED quando versão não encontrada (cache miss + Manager 404)")
    void deveFalharQuandoVersaoNaoEncontrada() {
        when(workflowCacheService.load("test-flow", "9.9.9"))
                .thenThrow(new com.orchestrator.exception.FlowNotFoundException("Não encontrado"));

        FlowExecutionResult r = service.execute("9.9.9", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Não encontrado");
    }
}
