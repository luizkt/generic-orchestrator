package com.orchestrator.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Representação em memória de um workflow. Após o refactor para o
 * service-portal-manager, esta classe NÃO é mais persistida pelo orquestrador
 * — o YAML é mantido pelo Manager e o orquestrador apenas faz o parse para
 * executar (e cacheia o resultado parseado em Redis).
 *
 * Todos os campos seguem a convenção em inglês — paths, payloads e YAML
 * são todos em inglês após o refactor REST.
 */
@Data
public class FlowDefinition {
    private String mongoId;
    @JsonProperty("id") private String flowId;
    private String description;
    private String version;
    private boolean active;
    private FlowContract contract;
    private List<IntegrationDefinition> integrations;
    private List<IntegrationDefinition> validations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
