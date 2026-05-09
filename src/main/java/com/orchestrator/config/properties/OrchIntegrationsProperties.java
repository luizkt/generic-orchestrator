package com.orchestrator.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "orch-integrations")
public class OrchIntegrationsProperties {

    private List<KafkaIntegrationProperties> kafkas = new ArrayList<>();
    private List<RabbitMqIntegrationProperties> rabbitmqs = new ArrayList<>();
}
