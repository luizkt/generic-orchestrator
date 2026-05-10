package com.orchestrator.manager;

import com.orchestrator.config.properties.ManagerProperties;
import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Auth server-to-server contra o service-portal-manager.
 * Mantém o JWT em memória e renova automaticamente quando próximo do vencimento.
 *
 * Estratégia idêntica à do BFF (`OrchestratorAuthService`) — refresh quando faltar
 * menos de 60s para expirar (assume TTL de 3600s, mesmo padrão do Manager).
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
        LoginResponse response = managerWebClient.post()
                .uri("/api/auth/login")
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
