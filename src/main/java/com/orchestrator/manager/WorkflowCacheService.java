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

import com.orchestrator.config.RedisCacheConfig;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.service.YamlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Camada que serve {@link FlowDefinition} a partir do Redis. Em cache miss, chama o Manager pelo
 * YAML, faz o parse e devolve — Spring Cache cacheia o retorno para a próxima chamada.
 *
 * <p>Chave do cache: `flowId + "_" + versao` (mesmo padrão do warm-up).
 *
 * <p>NOTE: o método precisa ser chamado de outro bean (proxy do Spring) — chamadas internas
 * (this.load(...)) não passam pelo proxy e não consultam o cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCacheService {

    private final ManagerWorkflowClient managerClient;
    private final YamlParserService yamlParser;

    @Cacheable(
            value = RedisCacheConfig.WORKFLOWS_CACHE,
            key = "#flowId + '_' + #versao",
            unless = "#result == null")
    public FlowDefinition load(String flowId, String versao) {
        log.debug("Cache miss para workflow {}/{} — buscando no Manager", flowId, versao);
        String yaml = managerClient.fetchYaml(flowId, versao);
        return yamlParser.parse(yaml);
    }

    @CacheEvict(value = RedisCacheConfig.WORKFLOWS_CACHE, key = "#flowId + '_' + #versao")
    public void evict(String flowId, String versao) {
        log.info("Removendo workflow {}/{} do cache", flowId, versao);
    }

    @CacheEvict(value = RedisCacheConfig.WORKFLOWS_CACHE, allEntries = true)
    public void evictAll() {
        log.info("Limpando cache de workflows completo");
    }
}
