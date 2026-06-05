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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    @DisplayName("Executa fluxo com sucesso (load via WorkflowCacheService)")
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

    @Test
    @DisplayName("Retorna FAILED quando integração obrigatória falha")
    void deveFalharQuandoIntegracaoObrigatoriaFalha() {
        integration.setContinueOnError(false);
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("Retorna PARTIAL_SUCCESS quando integração não-obrigatória falha")
    void devePartialSuccessQuandoOpcionalFalha() {
        integration.setContinueOnError(true);
        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenThrow(new RuntimeException("erro"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
    }

    @Test
    @DisplayName("Executa integrações na ordem definida")
    void deveExecutarEmOrdem() {
        IntegrationDefinition i2 = new IntegrationDefinition();
        i2.setId("step-2");
        i2.setOrder(2);
        i2.setType(IntegrationType.HTTP);
        i2.setHttp(new HttpIntegrationConfig());
        IntegrationDefinition i0 = new IntegrationDefinition();
        i0.setId("step-0");
        i0.setOrder(0);
        i0.setType(IntegrationType.HTTP);
        i0.setHttp(new HttpIntegrationConfig());
        flow.setIntegrations(List.of(i2, integration, i0));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(any(), any())).thenReturn(Map.of("ok", true));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(r.getResult()).containsKeys("step-0", "step-1", "step-2");
    }

    @Test
    @DisplayName("Retorna FAILED quando versão não encontrada (cache miss + Manager 404)")
    void deveFalharQuandoVersaoNaoEncontrada() {
        when(workflowCacheService.load("test-flow", "9.9.9"))
                .thenThrow(new com.orchestrator.exception.FlowNotFoundException("Não encontrado"));

        FlowExecutionResult r = service.execute("9.9.9", "test-flow", Map.of());
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Não encontrado");
    }

    @Test
    @DisplayName("Executa validações após integrações e retorna resultados separados")
    void deveExecutarValidacoesAposIntegracoes() {
        IntegrationDefinition validation = new IntegrationDefinition();
        validation.setId("val-1");
        validation.setOrder(1);
        validation.setType(IntegrationType.HTTP);
        validation.setHttp(new HttpIntegrationConfig());
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

    @Test
    @DisplayName("PARTIAL_SUCCESS quando validação opcional falha")
    void devePartialSuccessQuandoValidacaoOpcionalFalha() {
        IntegrationDefinition validation = new IntegrationDefinition();
        validation.setId("val-opt");
        validation.setOrder(1);
        validation.setType(IntegrationType.HTTP);
        validation.setHttp(new HttpIntegrationConfig());
        validation.setContinueOnError(true);
        flow.setValidations(List.of(validation));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(integration), any())).thenReturn(Map.of("ok", true));
        when(httpExecutor.execute(eq(validation), any()))
                .thenThrow(new RuntimeException("credit denied"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
        assertThat(r.getValidations()).containsKey("val-opt");
        assertThat(r.getValidations().get("val-opt")).isEqualTo(Map.of("error", "credit denied"));
    }

    @Test
    @DisplayName("FAILED quando validação obrigatória falha")
    void deveFalharQuandoValidacaoObrigatoriaFalha() {
        IntegrationDefinition validation = new IntegrationDefinition();
        validation.setId("val-required");
        validation.setOrder(1);
        validation.setType(IntegrationType.HTTP);
        validation.setHttp(new HttpIntegrationConfig());
        validation.setContinueOnError(false);
        flow.setValidations(List.of(validation));

        when(workflowCacheService.load("test-flow", "1.0.0")).thenReturn(flow);
        when(executorFactory.get(IntegrationType.HTTP)).thenReturn(httpExecutor);
        when(httpExecutor.execute(eq(integration), any())).thenReturn(Map.of("ok", true));
        when(httpExecutor.execute(eq(validation), any())).thenThrow(new RuntimeException("failed"));

        FlowExecutionResult r = service.execute("1.0.0", "test-flow", Map.of());

        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).contains("Required validation failed");
    }

    @Test
    @DisplayName("Flow sem validações executa normalmente (backward compat)")
    void fluxoSemValidacoesRodaNormalmente() {
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
