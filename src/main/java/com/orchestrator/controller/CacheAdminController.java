package com.orchestrator.controller;

import com.orchestrator.manager.WorkflowCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos para invalidação do cache de workflows no Redis.
 *
 * Consumido pelo service-portal-manager (dono dos dados) após update/deactivate
 * de um workflow, para que o orquestrador não sirva uma versão stale por até o TTL.
 *
 * Protegido por {@code anyRequest().authenticated()} no SecurityConfig — exige
 * Bearer JWT interno (admin/admin via {@code POST /api/auth/tokens}).
 */
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final WorkflowCacheService cacheService;

    /** Invalida a entrada de cache de um workflow específico (flowId + version). */
    @DeleteMapping("/workflows/{flowId}/versions/{version}")
    public ResponseEntity<Void> evictWorkflow(@PathVariable String flowId,
                                              @PathVariable String version) {
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
