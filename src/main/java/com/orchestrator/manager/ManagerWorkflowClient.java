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

import com.orchestrator.config.properties.ManagerProperties;
import com.orchestrator.exception.FlowNotFoundException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Cliente HTTP para o service-portal-manager. Encapsula: - autenticação Bearer via {@link
 * ManagerAuthService} - timeout configurável via {@code orchestrator.manager.timeout-ms} -
 * mapeamento de 404 do Manager para {@link FlowNotFoundException}
 *
 * <p>Usado exclusivamente em leitura — todo CRUD de workflows acontece no Manager (BFF chama lá
 * direto).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerWorkflowClient {

    private final WebClient managerWebClient;
    private final ManagerAuthService authService;
    private final ManagerProperties props;

    private String authHeader() {
        return "Bearer " + authService.getToken();
    }

    /** Lista os fluxos ativos no Manager. Vazio se o Manager devolver `[]`. */
    public List<WorkflowSummary> listActive() {
        try {
            List<WorkflowSummary> result =
                    managerWebClient
                            .get()
                            .uri("/manager/flows?status=active")
                            .header(HttpHeaders.AUTHORIZATION, authHeader())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<List<WorkflowSummary>>() {})
                            .timeout(Duration.ofMillis(props.getTimeoutMs()))
                            .block();
            return result != null ? result : Collections.emptyList();
        } catch (WebClientResponseException e) {
            log.error(
                    "Manager respondeu {} ao listar ativos: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }

    /** Recupera o YAML cru de um fluxo específico. Lança {@link FlowNotFoundException} para 404. */
    public String fetchYaml(String flowId, String versao) {
        try {
            String yaml =
                    managerWebClient
                            .get()
                            .uri("/manager/flows/{flowId}/versions/{versao}/yaml", flowId, versao)
                            .header(HttpHeaders.AUTHORIZATION, authHeader())
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(props.getTimeoutMs()))
                            .block();
            if (yaml == null || yaml.isBlank()) {
                throw new FlowNotFoundException(
                        "Manager retornou YAML vazio para " + flowId + "/" + versao);
            }
            return yaml;
        } catch (WebClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) {
                throw new FlowNotFoundException(
                        "Fluxo não encontrado no Manager: id=" + flowId + ", versao=" + versao);
            }
            log.error(
                    "Manager respondeu {} ao buscar YAML de {}/{}: {}",
                    status,
                    flowId,
                    versao,
                    e.getResponseBodyAsString());
            throw e;
        }
    }
}
