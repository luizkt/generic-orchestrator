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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestrator.config.properties.ManagerProperties;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class ManagerAuthServiceTest {

    private MockWebServer mockWebServer;
    private ManagerAuthService authService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient =
                WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        ManagerProperties props = new ManagerProperties();
        props.setBaseUrl(mockWebServer.url("/").toString());
        props.setUsername("admin");
        props.setPassword("admin");
        props.setTimeoutMs(5000);

        authService = new ManagerAuthService(webClient, props);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getToken faz login e devolve o token")
    void getTokenLogin() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"token\":\"jwt-1\",\"type\":\"Bearer\"}"));

        assertThat(authService.getToken()).isEqualTo("jwt-1");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/auth/tokens");
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getBody().readUtf8()).contains("admin");
    }

    @Test
    @DisplayName("getToken usa cache nas chamadas subsequentes")
    void getTokenCacheado() {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"token\":\"jwt-cached\"}"));

        assertThat(authService.getToken()).isEqualTo("jwt-cached");
        assertThat(authService.getToken()).isEqualTo("jwt-cached");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getToken lança quando Manager devolve resposta sem token")
    void semToken() {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"type\":\"Bearer\"}"));
        assertThatThrownBy(() -> authService.getToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem token");
    }
}
