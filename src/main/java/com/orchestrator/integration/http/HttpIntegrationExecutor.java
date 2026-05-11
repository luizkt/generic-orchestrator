package com.orchestrator.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.config.properties.OrchIntegrationsProperties;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.HttpIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.exception.RetriableHttpException;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpIntegrationExecutor implements IntegrationExecutor {

    private final WebClient webClient;
    private final TemplateResolverService templateResolver;
    private final ObjectMapper objectMapper;
    private final RetryRegistry httpRetryRegistry;
    private final CircuitBreakerRegistry httpCircuitBreakerRegistry;
    private final OrchIntegrationsProperties properties;

    @Override
    public IntegrationType getType() { return IntegrationType.HTTP; }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(IntegrationDefinition def, FlowExecutionContext ctx) {
        HttpIntegrationConfig http = def.getHttp();
        if (http == null) throw new IntegrationExecutionException("HTTP config missing: " + def.getId());

        String url = templateResolver.resolve(http.getUrl(), ctx);
        String method = http.getMethod() != null ? http.getMethod().toUpperCase() : "GET";
        String body = http.getBodyTemplate() != null ? templateResolver.resolve(http.getBodyTemplate(), ctx) : null;
        long timeout = http.getTimeout() > 0 ? http.getTimeout()
                : properties.getRetryConfiguration().getTimeout();
        List<Integer> retryableStatuses = properties.getRetryConfiguration().getRetryableHttpCondition();

        log.info("[HTTP] {} {} (id={})", method, url, def.getId());

        CircuitBreaker cb = httpCircuitBreakerRegistry.circuitBreaker(def.getId());
        Retry retry = httpRetryRegistry.retry(def.getId());

        // Ordem: Retry(CircuitBreaker(httpCall)) — cada tentativa passa pelo CB,
        // que registra falhas e pode abrir o circuito para proteger o serviço downstream.
        Supplier<String> httpCall = CircuitBreaker.decorateSupplier(cb,
                () -> doHttpCall(http, url, method, body, timeout, retryableStatuses));

        Supplier<String> withRetry = Retry.decorateSupplier(retry, httpCall);

        retry.getEventPublisher().onRetry(e ->
                log.warn("[HTTP] Retry #{} para '{}' após: {}", e.getNumberOfRetryAttempts(),
                        def.getId(), e.getLastThrowable().getMessage()));

        cb.getEventPublisher()
                .onStateTransition(e -> log.warn("[CB] '{}' transitou: {} → {}",
                        def.getId(), e.getStateTransition().getFromState(), e.getStateTransition().getToState()))
                .onCallNotPermitted(e -> log.warn("[CB] Chamada bloqueada pelo circuit breaker: {}", def.getId()));

        try {
            String resp = withRetry.get();
            if (resp == null || resp.isBlank()) return Map.of();
            try { return objectMapper.readValue(resp, Map.class); }
            catch (Exception e) { return Map.of("response", resp); }
        } catch (CallNotPermittedException e) {
            log.error("[HTTP] Circuit breaker open for '{}': {}", def.getId(), e.getMessage());
            throw new IntegrationExecutionException("Circuit breaker open for: " + def.getId(), e);
        } catch (Exception e) {
            log.error("[HTTP] Error after retries exhausted for '{}': {}", def.getId(), e.getMessage());
            throw new IntegrationExecutionException("HTTP integration failed: " + def.getId(), e);
        }
    }

    private String doHttpCall(HttpIntegrationConfig http, String url, String method,
                               String body, long timeout, List<Integer> retryableStatuses) {
        WebClient.RequestBodySpec spec = webClient.method(HttpMethod.valueOf(method)).uri(url);
        if (http.getHeaders() != null) http.getHeaders().forEach(spec::header);

        try {
            if (body != null && !body.isBlank()) {
                return spec.bodyValue(body).retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout)).block();
            }
            return spec.retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout)).block();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (retryableStatuses.contains(status)) {
                log.debug("[HTTP] Status {} é retryable para '{}'", status, url);
                throw new RetriableHttpException("HTTP " + status + " retryable", status);
            }
            // Status não retryable (ex.: 404) — propaga sem retry
            throw e;
        }
        // Erros de rede/timeout (não WebClientResponseException) sobem naturalmente
        // e são retentados conforme o predicate configurado em HttpResilienceConfig
    }
}
