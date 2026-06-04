package com.orchestrator.controller;

import com.orchestrator.manager.WorkflowCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CacheAdminControllerTest {

    private final WorkflowCacheService cacheService = mock(WorkflowCacheService.class);
    private final CacheAdminController controller = new CacheAdminController(cacheService);

    @Test
    @DisplayName("DELETE de workflow específico delega para evict(flowId, version) e devolve 204")
    void evictWorkflowDelegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.evictWorkflow("create-order-v1", "1.0.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(cacheService).evict("create-order-v1", "1.0.0");
        verifyNoMoreInteractions(cacheService);
    }

    @Test
    @DisplayName("DELETE de todos os workflows delega para evictAll() e devolve 204")
    void evictAllDelegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.evictAllWorkflows();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(cacheService).evictAll();
        verifyNoMoreInteractions(cacheService);
    }
}
