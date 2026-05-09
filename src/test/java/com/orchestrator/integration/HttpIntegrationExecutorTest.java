package com.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.HttpIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.http.HttpIntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpIntegrationExecutorTest {

    private MockWebServer mockWebServer;
    private HttpIntegrationExecutor executor;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        executor = new HttpIntegrationExecutor(
                WebClient.builder().build(),
                new TemplateResolverService(),
                new ObjectMapper());
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

        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("test"); def.setTipo(IntegrationType.HTTP);
        HttpIntegrationConfig http = new HttpIntegrationConfig();
        http.setUrl(mockWebServer.url("/test").toString());
        http.setMetodo("GET");
        http.setTimeout(5000);
        def.setHttp(http);

        Object result = executor.execute(def, new FlowExecutionContext());
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result)).containsEntry("status", "ok");
    }

    @Test @DisplayName("Falha quando o servidor retorna erro 500")
    void deveLancarErroEm5xx() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("test"); def.setTipo(IntegrationType.HTTP);
        HttpIntegrationConfig http = new HttpIntegrationConfig();
        http.setUrl(mockWebServer.url("/erro").toString());
        http.setMetodo("GET");
        http.setTimeout(2000);
        def.setHttp(http);

        assertThatThrownBy(() -> executor.execute(def, new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class);
    }

    @Test @DisplayName("Resolve placeholders na URL e body")
    void deveResolverPlaceholders() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200).setBody("{\"ok\":true}"));

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContrato(Map.of("id", "123"));

        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("test"); def.setTipo(IntegrationType.HTTP);
        HttpIntegrationConfig http = new HttpIntegrationConfig();
        http.setUrl(mockWebServer.url("/api/{{contrato.id}}").toString());
        http.setMetodo("POST");
        http.setBodyTemplate("{\"id\":\"{{contrato.id}}\"}");
        http.setHeaders(Map.of("Content-Type", "application/json"));
        http.setTimeout(5000);
        def.setHttp(http);

        executor.execute(def, ctx);
        var request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("123");
        assertThat(request.getBody().readUtf8()).contains("\"id\":\"123\"");
    }
}
