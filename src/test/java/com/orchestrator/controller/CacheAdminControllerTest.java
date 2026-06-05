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
package com.orchestrator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.orchestrator.manager.WorkflowCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
