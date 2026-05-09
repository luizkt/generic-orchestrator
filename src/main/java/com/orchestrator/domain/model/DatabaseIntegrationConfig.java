package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class DatabaseIntegrationConfig {
    private DatabaseOperation operacao;
    private String colecao;
    private String documentoTemplate;
    private String filtroTemplate;
    private ResponseMapping mapeamentoResposta;
}
