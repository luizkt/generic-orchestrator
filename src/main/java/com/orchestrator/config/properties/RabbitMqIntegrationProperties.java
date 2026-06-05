package com.orchestrator.config.properties;

import lombok.Data;

@Data
public class RabbitMqIntegrationProperties {

    private String id;
    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
}
