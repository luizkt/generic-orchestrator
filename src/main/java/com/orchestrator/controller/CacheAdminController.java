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

import com.orchestrator.manager.WorkflowCacheService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos para invalidação do cache de workflows no Redis.
 *
 * <p>Consumido pelo service-portal-manager (dono dos dados) após update/deactivate de um workflow,
 * para que o orquestrador não sirva uma versão stale por até o TTL.
 *
 * <p>Protegido por {@code anyRequest().authenticated()} no SecurityConfig — exige Bearer JWT
 * interno (admin/admin via {@code POST /api/auth/tokens}).
 */
@Tag(name = "Cache Admin")
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final WorkflowCacheService cacheService;

    /** Invalida a entrada de cache de um workflow específico (flowId + version). */
    @DeleteMapping("/workflows/{flowId}/versions/{version}")
    public ResponseEntity<Void> evictWorkflow(
            @PathVariable String flowId, @PathVariable String version) {
        cacheService.evict(flowId, version);
        return ResponseEntity.noContent().build();
    }

    /** Invalida todas as entradas do cache de workflows. */
    @DeleteMapping("/workflows")
    public ResponseEntity<Void> evictAllWorkflows() {
        cacheService.evictAll();
        return ResponseEntity.noContent().build();
    }
}
