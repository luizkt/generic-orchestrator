package com.orchestrator.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orchestrator.manager")
public class ManagerProperties {

    /** Base URL do service-portal-manager (ex: http://manager:8082). */
    private String baseUrl;

    /** Credenciais server-to-server para login no Manager. */
    private String username;

    /** Senha server-to-server. */
    private String password;

    /** Timeout (ms) para chamadas HTTP ao Manager. */
    private long timeoutMs = 5000;
}
