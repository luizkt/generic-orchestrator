package com.orchestrator.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "flow_definitions")
public class FlowDefinition {
    @Id private String mongoId;
    @JsonProperty("id") @Indexed private String flowId;
    private String descricao;
    private String versao;
    private boolean ativo;
    private FlowContract contrato;
    private List<IntegrationDefinition> integracoes;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
