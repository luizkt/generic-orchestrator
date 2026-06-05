package com.orchestrator.config;

import com.orchestrator.config.properties.ManagerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/** Bean dedicado de WebClient para chamadas ao service-portal-manager. */
@Configuration
@EnableConfigurationProperties(ManagerProperties.class)
public class ManagerWebClientConfig {

    @Bean
    public WebClient managerWebClient(ManagerProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
