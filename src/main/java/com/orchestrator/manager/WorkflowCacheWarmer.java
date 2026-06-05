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
package com.orchestrator.manager;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warm-up do cache de workflows na inicialização: chama `GET /manager/flows?status=active` e popula
 * o Redis para cada fluxo ativo.
 *
 * <p>Quando um fluxo falhar individualmente (ex.: YAML antigo sem `yamlContent` vindo de docs
 * criados pelo orquestrador antes da migração) o erro é logado e o warm-up continua — não impede o
 * orquestrador de subir.
 *
 * <p>Pode ser desabilitado via `orchestrator.cache.workflows.warm-up-enabled=false` (útil para
 * tests sem Manager disponível).
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
                    cacheService.load(w.getFlowId(), w.getVersion());
                    ok++;
                } catch (Exception e) {
                    log.warn(
                            "Falha ao carregar fluxo {}/{} no warm-up: {}",
                            w.getFlowId(),
                            w.getVersion(),
                            e.getMessage());
                    falha++;
                }
            }
            log.info("Warm-up concluído: {} sucesso(s), {} falha(s)", ok, falha);
        } catch (Exception e) {
            log.error(
                    "Warm-up do cache falhou: {} — orquestrador segue subindo "
                            + "e fará lazy-load no primeiro request",
                    e.getMessage());
        }
    }
}
