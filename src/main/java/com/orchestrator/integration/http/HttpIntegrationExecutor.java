package com.orchestrator.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.HttpIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpIntegrationExecutor implements IntegrationExecutor {

    private final WebClient webClient;
    private final TemplateResolverService templateResolver;
    private final ObjectMapper objectMapper;

    @Override
    public IntegrationType getType() { return IntegrationType.HTTP; }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(IntegrationDefinition def, FlowExecutionContext ctx) {
        HttpIntegrationConfig http = def.getHttp();
        if (http == null) throw new IntegrationExecutionException("Configuração HTTP ausente: " + def.getId());
        try {
            String url = templateResolver.resolve(http.getUrl(), ctx);
            String method = http.getMetodo() != null ? http.getMetodo().toUpperCase() : "GET";
            String body = http.getBodyTemplate() != null ? templateResolver.resolve(http.getBodyTemplate(), ctx) : null;
            int timeout = http.getTimeout() > 0 ? http.getTimeout() : 30000;

            log.info("[HTTP] {} {}", method, url);

            WebClient.RequestBodySpec spec = webClient.method(HttpMethod.valueOf(method)).uri(url);
            if (http.getHeaders() != null) http.getHeaders().forEach(spec::header);

            String resp;
            if (body != null && !body.isBlank()) {
                resp = spec.bodyValue(body).retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout)).block();
            } else {
                resp = spec.retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout)).block();
            }

            if (resp == null || resp.isBlank()) return Map.of();
            try { return objectMapper.readValue(resp, Map.class); }
            catch (Exception e) { return Map.of("response", resp); }
        } catch (Exception e) {
            log.error("Erro HTTP em '{}': {}", def.getId(), e.getMessage());
            throw new IntegrationExecutionException("Falha na integração HTTP: " + def.getId(), e);
        }
    }
}
