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
import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auth server-to-server contra o service-portal-manager. Mantém o JWT em memória e renova
 * automaticamente quando próximo do vencimento.
 *
 * <p>Estratégia idêntica à do BFF (`OrchestratorAuthService`) — refresh quando faltar menos de 60s
 * para expirar (assume TTL de 3600s, mesmo padrão do Manager).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerAuthService {

    private final WebClient managerWebClient;
    private final ManagerProperties props;

    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public synchronized String getToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }
        return token;
    }

    private void refreshToken() {
        log.debug("Renovando token do service-portal-manager");
        LoginResponse response =
                managerWebClient
                        .post()
                        .uri("/api/auth/tokens")
                        .bodyValue(new LoginRequest(props.getUsername(), props.getPassword()))
                        .retrieve()
                        .bodyToMono(LoginResponse.class)
                        .timeout(Duration.ofMillis(props.getTimeoutMs()))
                        .block();
        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Manager retornou login sem token");
        }
        this.token = response.getToken();
        this.tokenExpiry = Instant.now().plusSeconds(3600);
        log.debug("Token do Manager renovado com sucesso");
    }
}
