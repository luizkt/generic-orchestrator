package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class IntegrationDefinition {
    private String id;
    private int ordem;
    private IntegrationType tipo;
    private QueueProvider provider;
    private String descricao;
    private boolean continuarEmErro;
    private HttpIntegrationConfig http;
    private QueueIntegrationConfig queue;
}
