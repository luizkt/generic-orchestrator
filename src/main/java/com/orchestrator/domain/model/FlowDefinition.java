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
 * O campo `mongoId` segue presente para compatibilidade com workflows antigos
 * que ainda venham com `_id` no YAML deserializado, mas não tem mais
 * significado de persistência.
 */
@Data
public class FlowDefinition {
    private String mongoId;
    @JsonProperty("id") private String flowId;
    private String descricao;
    private String versao;
    private boolean ativo;
    private FlowContract contrato;
    private List<IntegrationDefinition> integracoes;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
