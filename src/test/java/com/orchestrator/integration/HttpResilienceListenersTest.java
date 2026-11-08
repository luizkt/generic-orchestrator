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
package com.orchestrator.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.orchestrator.integration.http.HttpIntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/** Garante que os listeners de Retry/CircuitBreaker são registrados uma única vez por instância. */
class HttpResilienceListenersTest {

    private static final int EXECUCOES = 4;
    private static final int RETRIES_POR_EXECUCAO = 2; // maximumAttempts = 3

    private MockWebServer mockWebServer;
    private HttpIntegrationExecutor executor;
    private CircuitBreakerRegistry cbRegistry;
    private ListAppender<ILoggingEvent> appender;
    private Logger executorLogger;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OrchIntegrationsProperties props = buildTestProperties();
        HttpResilienceConfig config = new HttpResilienceConfig(props);
        RetryRegistry retryRegistry = config.httpRetryRegistry();
        cbRegistry = config.httpCircuitBreakerRegistry();
        executor =
                new HttpIntegrationExecutor(
                        WebClient.builder().build(),
                        new TemplateResolverService(),
                        new ObjectMapper(),
                        retryRegistry,
                        cbRegistry,
                        props);

        executorLogger = (Logger) LoggerFactory.getLogger(HttpResilienceConfig.class);
        appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() throws IOException {
        executorLogger.detachAppender(appender);
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName(
            "Cada retry é logado uma única vez, mesmo após várias execuções da mesma integração")
    void retryLogadoUmaVezPorTentativa() {
        for (int i = 1; i <= EXECUCOES; i++) {
            for (int a = 0; a < 3; a++) {
                mockWebServer.enqueue(new MockResponse().setResponseCode(500));
            }
            appender.list.clear();

            assertThatThrownBy(() -> executor.execute(buildDef(), new FlowExecutionContext()))
                    .isInstanceOf(IntegrationExecutionException.class);

            assertThat(messages("[HTTP] Retry #"))
                    .as("linhas de retry na execução %d", i)
                    .hasSize(RETRIES_POR_EXECUCAO);
        }
    }

    @Test
    @DisplayName("Transição e bloqueio do circuit breaker são logados uma única vez")
    void circuitBreakerLogadoUmaVez() {
        for (int i = 0; i < EXECUCOES; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            executor.execute(buildDef(), new FlowExecutionContext());
        }
        appender.list.clear();

        var cb = cbRegistry.circuitBreaker("listener-integration");
        cb.transitionToOpenState();

        assertThat(messages("[CB] 'listener-integration' transitou")).hasSize(1);

        assertThatThrownBy(() -> executor.execute(buildDef(), new FlowExecutionContext()))
                .isInstanceOf(IntegrationExecutionException.class);
        assertThat(messages("[CB] Chamada bloqueada")).hasSize(1);
    }

    private List<String> messages(String prefix) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith(prefix))
                .toList();
    }

    private IntegrationDefinition buildDef() {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("listener-integration");
        def.setType(IntegrationType.HTTP);
        HttpIntegrationConfig http = new HttpIntegrationConfig();
        http.setUrl(mockWebServer.url("/test").toString());
        http.setMethod("GET");
        http.setTimeout(5000);
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
        cb.setSlidingWindowSize(1000);
        cb.setSlidingWindowSizeType("COUNT");
        cb.setMinimumNumberCalls(1000);
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
