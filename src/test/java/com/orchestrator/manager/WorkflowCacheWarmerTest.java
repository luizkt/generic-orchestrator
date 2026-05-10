package com.orchestrator.manager;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.FlowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCacheWarmerTest {

    @Mock private ManagerWorkflowClient managerClient;
    @Mock private WorkflowCacheService cacheService;

    @InjectMocks private WorkflowCacheWarmer warmer;

    @BeforeEach
    void enableWarmUp() {
        ReflectionTestUtils.setField(warmer, "enabled", true);
    }

    @Test @DisplayName("warmUp itera lista de ativos e popula o cache")
    void deveCarregarTodos() {
        when(managerClient.listActive()).thenReturn(List.of(
                summary("a", "1.0"), summary("b", "2.0")));
        when(cacheService.load(any(), any())).thenReturn(new FlowDefinition());

        warmer.warmUp();

        verify(cacheService).load("a", "1.0");
        verify(cacheService).load("b", "2.0");
    }

    @Test @DisplayName("warmUp continua mesmo quando um fluxo falha")
    void erroIndividualNaoAborta() {
        when(managerClient.listActive()).thenReturn(List.of(
                summary("a", "1.0"), summary("b", "2.0"), summary("c", "3.0")));
        when(cacheService.load("a", "1.0")).thenReturn(new FlowDefinition());
        when(cacheService.load("b", "2.0")).thenThrow(new FlowNotFoundException("legacy"));
        when(cacheService.load("c", "3.0")).thenReturn(new FlowDefinition());

        warmer.warmUp();

        verify(cacheService, times(3)).load(any(), any());
    }

    @Test @DisplayName("warmUp não lança quando Manager está indisponível")
    void managerInacessivel() {
        when(managerClient.listActive()).thenThrow(new RuntimeException("manager off"));
        warmer.warmUp(); // não propaga
        verify(cacheService, never()).load(any(), any());
    }

    @Test @DisplayName("warmUp respeita flag enabled=false")
    void desabilitado() {
        ReflectionTestUtils.setField(warmer, "enabled", false);
        warmer.warmUp();
        verify(managerClient, never()).listActive();
        verify(cacheService, never()).load(any(), any());
    }

    private WorkflowSummary summary(String id, String versao) {
        return new WorkflowSummary(id, versao, true);
    }
}
