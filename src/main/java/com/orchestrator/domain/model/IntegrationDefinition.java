package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class IntegrationDefinition {
    private String id;
    private int order;
    private IntegrationType type;
    private QueueProvider provider;
    private String description;
    private boolean continueOnError;
    private HttpIntegrationConfig http;
    private QueueIntegrationConfig queue;
}
