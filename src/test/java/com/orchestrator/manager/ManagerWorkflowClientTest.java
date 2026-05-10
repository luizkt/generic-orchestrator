package com.orchestrator.manager;

import com.orchestrator.config.properties.ManagerProperties;
import com.orchestrator.exception.FlowNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagerWorkflowClientTest {

    private MockWebServer mockWebServer;
    private ManagerAuthService authService;
    private ManagerWorkflowClient client;
    private ManagerProperties props;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        props = new ManagerProperties();
        props.setBaseUrl(mockWebServer.url("/").toString());
        props.setUsername("admin");
        props.setPassword("admin");
        props.setTimeoutMs(5000);

        authService = mock(ManagerAuthService.class);
        when(authService.getToken()).thenReturn("test-token");

        client = new ManagerWorkflowClient(webClient, authService, props);
    }

    @AfterEach
    void tearDown() throws IOException { mockWebServer.shutdown(); }

    @Test @DisplayName("listActive devolve lista parseada e envia Authorization")
    void listActiveOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"flowId\":\"a\",\"versao\":\"1.0\",\"ativo\":true}," +
                        "{\"flowId\":\"b\",\"versao\":\"2.0\",\"ativo\":true}]"));

        var ativos = client.listActive();
        assertThat(ativos).hasSize(2);
        assertThat(ativos.get(0).getFlowId()).isEqualTo("a");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/workflows/active");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test @DisplayName("listActive devolve lista vazia quando body é null")
    void listActiveVazio() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        assertThat(client.listActive()).isEmpty();
    }

    @Test @DisplayName("listActive propaga WebClientResponseException em 5xx")
    void listActiveErroServidor() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        assertThatThrownBy(() -> client.listActive())
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test @DisplayName("fetchYaml retorna o body cru e envia Authorization")
    void fetchYamlOk() throws InterruptedException {
        String yaml = "fluxo:\n  id: x\n";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/x-yaml").setBody(yaml));

        String result = client.fetchYaml("flow-x", "1.0.0");
        assertThat(result).isEqualTo(yaml);

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/workflows/flow-x/1.0.0/yaml");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test @DisplayName("fetchYaml lança FlowNotFoundException quando body vazio")
    void fetchYamlBodyVazio() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        assertThatThrownBy(() -> client.fetchYaml("flow-x", "1.0.0"))
                .isInstanceOf(FlowNotFoundException.class)
                .hasMessageContaining("YAML vazio");
    }

    @Test @DisplayName("fetchYaml mapeia 404 do Manager para FlowNotFoundException")
    void fetchYaml404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"FLOW_NOT_FOUND\"}"));
        assertThatThrownBy(() -> client.fetchYaml("nope", "9.9.9"))
                .isInstanceOf(FlowNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test @DisplayName("fetchYaml propaga WebClientResponseException em 5xx")
    void fetchYaml500() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        assertThatThrownBy(() -> client.fetchYaml("flow-x", "1.0.0"))
                .isInstanceOf(WebClientResponseException.class);
    }
}
