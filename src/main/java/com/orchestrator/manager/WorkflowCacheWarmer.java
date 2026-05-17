package com.orchestrator.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Warm-up do cache de workflows na inicialização: chama
 * `GET /manager/flows?status=active` e popula o Redis para cada fluxo ativo.
 *
 * Quando um fluxo falhar individualmente (ex.: YAML antigo sem `yamlContent`
 * vindo de docs criados pelo orquestrador antes da migração) o erro é logado
 * e o warm-up continua — não impede o orquestrador de subir.
 *
 * Pode ser desabilitado via `orchestrator.cache.workflows.warm-up-enabled=false`
 * (útil para tests sem Manager disponível).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCacheWarmer {

    private final ManagerWorkflowClient managerClient;
    private final WorkflowCacheService cacheService;

    @Value("${orchestrator.cache.workflows.warm-up-enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            log.info("Warm-up do cache de workflows desabilitado");
            return;
        }
        log.info("Iniciando warm-up do cache de workflows...");
        try {
            List<WorkflowSummary> ativos = managerClient.listActive();
            log.info("Manager devolveu {} fluxo(s) ativo(s)", ativos.size());
            int ok = 0, falha = 0;
            for (WorkflowSummary w : ativos) {
                try {
                    cacheService.load(w.getFlowId(), w.getVersao());
                    ok++;
                } catch (Exception e) {
                    log.warn("Falha ao carregar fluxo {}/{} no warm-up: {}",
                            w.getFlowId(), w.getVersao(), e.getMessage());
                    falha++;
                }
            }
            log.info("Warm-up concluído: {} sucesso(s), {} falha(s)", ok, falha);
        } catch (Exception e) {
            log.error("Warm-up do cache falhou: {} — orquestrador segue subindo " +
                    "e fará lazy-load no primeiro request", e.getMessage());
        }
    }
}
