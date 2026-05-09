package com.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.orchestrator.config.properties")
public class GenericOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenericOrchestratorApplication.class, args);
    }
}
