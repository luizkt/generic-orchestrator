package com.orchestrator.service;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.integration.IntegrationExecutorFactory;
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

    @Mock private FlowDefinitionService flowDefinitionService;
    @Mock private ContractValidationService contractValidationService;
    @Mock private IntegrationExecutorFactory executorFactory;
    @Mock private IntegrationExecutor httpExecutor;

    @InjectMocks private OrchestrationService service;

    private FlowDefinition flow;
    private IntegrationDefinition integration;

    @BeforeEach
    void setUp() {
        FlowContract contract = new FlowContract();
        contract.setCampos(List.of());
        integration = new IntegrationDefinition();
        integration.setId("step-1");
        integration.setOrdem(1);
        integration.setTipo(IntegrationType.HTTP);
        integration.setHttp(new HttpIntegrationConfig());
        flow = new FlowDefinition();
        flow.setFlowId("test-flow");
        flow.setAtivo(true);
        flow.setContrato(contract);
        flow.setIntegracoes(List.of(integration));
    }

    @Test @DisplayName("Executa fluxo com sucesso")
    void deveExecutarFluxoComSucesso() {
        when(flowDefinitionService.findActiveByFlowIdAndVersion("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult result = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getResultado()).containsKey("step-1");
        verify(contractValidationService).validate(any(), any());
    }

    @Test @DisplayName("Retorna FAILED quando integração obrigatória falha")
    void deveFalharQuandoIntegracaoObrigatoriaFalha() {
        integration.setContinuarEmErro(false);
        when(flowDefinitionService.findActiveByFlowIdAndVersion("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test @DisplayName("Retorna PARTIAL_SUCCESS quando integração não-obrigatória falha")
    void devePartialSuccessQuandoOpcionalFalha() {
        integration.setContinuarEmErro(true);
        when(flowDefinitionService.findActiveByFlowIdAndVersion("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
    }

    @Test @DisplayName("Executa integrações na ordem definida")
    void deveExecutarEmOrdem() {
        IntegrationDefinition i2 = new IntegrationDefinition();
        i2.setId("step-2"); i2.setOrdem(2); i2.setTipo(IntegrationType.HTTP);
        i2.setHttp(new HttpIntegrationConfig());
        IntegrationDefinition i0 = new IntegrationDefinition();
        i0.setId("step-0"); i0.setOrdem(0); i0.setTipo(IntegrationType.HTTP);
        i0.setHttp(new HttpIntegrationConfig());
        flow.setIntegracoes(List.of(i2, integration, i0));

        when(flowDefinitionService.findActiveByFlowIdAndVersion("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResultado()).containsKeys("step-0", "step-1", "step-2");
    }

    @Test @DisplayName("Retorna FAILED quando versão não encontrada")
    void deveFalharQuandoVersaoNaoEncontrada() {
        when(flowDefinitionService.findActiveByFlowIdAndVersion("test-flow", "9.9.9"))
                .thenThrow(new com.orchestrator.exception.FlowNotFoundException("Não encontrado"));

        FlowExecutionResult r = service.execute("9.9.9", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Não encontrado");
    }
}
