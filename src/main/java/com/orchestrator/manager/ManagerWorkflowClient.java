package com.orchestrator.manager;

import com.orchestrator.config.properties.ManagerProperties;
import com.orchestrator.exception.FlowNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Cliente HTTP para o service-portal-manager. Encapsula:
 *   - autenticação Bearer via {@link ManagerAuthService}
 *   - timeout configurável via {@code orchestrator.manager.timeout-ms}
 *   - mapeamento de 404 do Manager para {@link FlowNotFoundException}
 *
 * Usado exclusivamente em leitura — todo CRUD de workflows acontece no Manager
 * (BFF chama lá direto).
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
            List<WorkflowSummary> result = managerWebClient.get()
                    .uri("/manager/workflows/active")
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<WorkflowSummary>>() {})
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();
            return result != null ? result : Collections.emptyList();
        } catch (WebClientResponseException e) {
            log.error("Manager respondeu {} ao listar ativos: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /** Recupera o YAML cru de um fluxo específico. Lança {@link FlowNotFoundException} para 404. */
    public String fetchYaml(String flowId, String versao) {
        try {
            String yaml = managerWebClient.get()
                    .uri("/manager/workflows/{flowId}/{versao}/yaml", flowId, versao)
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();
            if (yaml == null || yaml.isBlank()) {
                throw new FlowNotFoundException("Manager retornou YAML vazio para " + flowId + "/" + versao);
            }
            return yaml;
        } catch (WebClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) {
                throw new FlowNotFoundException(
                        "Fluxo não encontrado no Manager: id=" + flowId + ", versao=" + versao);
            }
            log.error("Manager respondeu {} ao buscar YAML de {}/{}: {}",
                    status, flowId, versao, e.getResponseBodyAsString());
            throw e;
        }
    }
}
