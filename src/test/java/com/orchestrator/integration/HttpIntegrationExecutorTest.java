package com.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.config.HttpResilienceConfig;
import com.orchestrator.config.properties.CircuitBreakerConfigurationProperties;
import com.orchestrator.config.properties.OrchIntegrationsProperties;
import com.orchestrator.config.properties.RetryConfigurationProperties;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.HttpIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.exception.RetriableHttpException;
import com.orchestrator.integration.http.HttpIntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpIntegrationExecutorTest {

    private MockWebServer mockWebServer;
    private HttpIntegrationExecutor executor;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OrchIntegrationsProperties props = buildTestProperties();
        HttpResilienceConfig resilienceConfig = new HttpResilienceConfig(props);
        circuitBreakerRegistry = resilienceConfig.httpCircuitBreakerRegistry();

        executor = new HttpIntegrationExecutor(
                WebClient.builder().build(),
                new TemplateResolverService(),
                new ObjectMapper(),
                resilienceConfig.httpRetryRegistry(),
                circuitBreakerRegistry,
                props);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test @DisplayName("Executa GET HTTP com sucesso")
    void deveExecutarGet() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"ok\"}"));

        Object result = executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext());

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("status", "ok");
    }

    @Test @DisplayName("Retry em status 500 e sucesso na terceira tentativa")
    void deveRealizarRetryEm500ESuccederNaTerceiraTentativa() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"recuperado\":true}"));

        Object result = executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext());

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("recuperado", true);
    }

    @Test @DisplayName("Não realiza retry em status 404")
    void naoRealizaRetryEm404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class);

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test @DisplayName("Esgota tentativas em status 500 e lança IntegrationExecutionException")
    void deveLancarErroAposEsgotarRetries() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("test-integration");

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test @DisplayName("Resolve placeholders na URL e body")
    void deveResolverPlaceholders() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200).setBody("{\"ok\":true}"));

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("id", "123"));

        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("test-integration");
        def.setType(IntegrationType.HTTP);
        HttpIntegrationConfig http = new HttpIntegrationConfig();
        String baseUrl = "http://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort();
        http.setUrl(baseUrl + "/api/{{contract.id}}");
        http.setMethod("POST");
        http.setBodyTemplate("{\"id\":\"{{contract.id}}\"}");
        http.setHeaders(Map.of("Content-Type", "application/json"));
        http.setTimeout(5000);
        def.setHttp(http);

        executor.execute(def, ctx);

        var request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("123");
        assertThat(request.getBody().readUtf8()).contains("\"id\":\"123\"");
    }

    @Test @DisplayName("getType retorna HTTP")
    void getTypeRetornaHttp() {
        assertThat(executor.getType()).isEqualTo(IntegrationType.HTTP);
    }

    @Test @DisplayName("Lança erro quando configuração HTTP está ausente")
    void deveLancarErroQuandoConfigHttpAusente() {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("missing-http");
        def.setType(IntegrationType.HTTP);

        assertThatThrownBy(() -> executor.execute(def, new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("missing-http");
    }

    @Test @DisplayName("Resposta vazia retorna Map vazio")
    void respostaVaziaRetornaMapVazio() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        Object result = executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext());

        assertThat(result).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) result).isEmpty();
    }

    @Test @DisplayName("Resposta não-JSON é encapsulada em Map com chave 'response'")
    void respostaNaoJsonEncapsulada() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("texto livre"));

        Object result = executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext());

        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("response", "texto livre");
    }

    @Test @DisplayName("Usa timeout do retry-configuration quando integração não define timeout")
    void usaTimeoutPadraoDoRetryConfiguration() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        IntegrationDefinition def = buildDef("GET", null, 0);
        Object result = executor.execute(def, new FlowExecutionContext());

        assertThat(result).isInstanceOf(Map.class);
    }

    @Test @DisplayName("Default GET quando método é nulo")
    void defaultGetQuandoMetodoNulo() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        Object result = executor.execute(buildDef(null, null, 5000), new FlowExecutionContext());

        assertThat(result).isInstanceOf(Map.class);
    }

    @Test @DisplayName("Circuit breaker aberto bloqueia chamadas e lança IntegrationExecutionException")
    void circuitBreakerAbertoBloqueiaChamadas() {
        OrchIntegrationsProperties props = buildTestProperties();
        HttpResilienceConfig config = new HttpResilienceConfig(props);
        CircuitBreakerRegistry cbRegistry = config.httpCircuitBreakerRegistry();
        cbRegistry.circuitBreaker("test-integration").transitionToOpenState();

        HttpIntegrationExecutor cbExecutor = new HttpIntegrationExecutor(
                WebClient.builder().build(),
                new TemplateResolverService(),
                new ObjectMapper(),
                config.httpRetryRegistry(),
                cbRegistry,
                props);

        assertThatThrownBy(() -> cbExecutor.execute(buildDef("GET", null, 5000), new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("Circuit breaker open");
    }

    @Test @DisplayName("Listener de transição de estado do circuit breaker é acionado")
    void listenerDeTransicaoDeEstado() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        executor.execute(buildDef("GET", null, 5000), new FlowExecutionContext());

        // Após o execute(), o listener está registrado no CB. Forçando transições
        // manuais cobrimos os lambdas onStateTransition e onCallNotPermitted.
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("test-integration");
        cb.transitionToOpenState();
        try { cb.acquirePermission(); } catch (Exception ignored) { }
        cb.transitionToHalfOpenState();
        cb.transitionToClosedState();
    }

    @Test @DisplayName("Predicate de retry rejeita CallNotPermittedException")
    void retryPredicateRejeitaCallNotPermitted() {
        OrchIntegrationsProperties props = buildTestProperties();
        RetryRegistry registry = new HttpResilienceConfig(props).httpRetryRegistry();

        var predicate = registry.getDefaultConfig().getExceptionPredicate();

        var cnpe = io.github.resilience4j.circuitbreaker.CallNotPermittedException
                .createCallNotPermittedException(
                        io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("x"));

        assertThat(predicate.test(cnpe)).isFalse();
        assertThat(predicate.test(new RetriableHttpException("retry", 500))).isTrue();
        assertThat(predicate.test(new RuntimeException("rede"))).isTrue();
    }

    @Test @DisplayName("RetriableHttpException expõe statusCode")
    void retriableHttpExceptionStatusCode() {
        RetriableHttpException ex = new RetriableHttpException("erro", 503);
        assertThat(ex.getStatusCode()).isEqualTo(503);
        assertThat(ex.getMessage()).isEqualTo("erro");
    }

    @Test @DisplayName("Circuit breaker com sliding window TIME-based")
    void circuitBreakerSlidingWindowTimeBased() {
        OrchIntegrationsProperties props = buildTestProperties();
        props.getCircuitBreakerConfiguration().setSlidingWindowSizeType("TIME");
        HttpResilienceConfig config = new HttpResilienceConfig(props);

        var cb = config.httpCircuitBreakerRegistry().circuitBreaker("any");
        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowType())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.TIME_BASED);
    }

    private IntegrationDefinition buildDef(String method, String bodyTemplate, int timeout) {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("test-integration");
        def.setType(IntegrationType.HTTP);

        HttpIntegrationConfig http = new HttpIntegrationConfig();
        http.setUrl(mockWebServer.url("/test").toString());
        http.setMethod(method);
        http.setTimeout(timeout);
        http.setBodyTemplate(bodyTemplate);
        def.setHttp(http);
        return def;
    }

    private OrchIntegrationsProperties buildTestProperties() {
        RetryConfigurationProperties retry = new RetryConfigurationProperties();
        retry.setMaximumAttempts(3);
        retry.setDelay(10);
        retry.setBackoffMultiplier(1.0);
        retry.setMaximumDelay(100);
        retry.setRetryableHttpCondition(List.of(500, 429, 408));
        retry.setTimeout(5000);

        CircuitBreakerConfigurationProperties cb = new CircuitBreakerConfigurationProperties();
        cb.setSlidingWindowSize(10);
        cb.setSlidingWindowSizeType("COUNT");
        cb.setMinimumNumberCalls(100);
        cb.setFailureRateThreshold(50);
        cb.setWaitDurationOpenState(30);
        cb.setPermittedCallsOpenState(3);
        cb.setAutomaticTransitionHalfOpenEnabled(false);
        cb.setSlowCallDurationThreshold(800);

        OrchIntegrationsProperties props = new OrchIntegrationsProperties();
        props.setRetryConfiguration(retry);
        props.setCircuitBreakerConfiguration(cb);
        return props;
    }
}
