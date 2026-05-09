package com.orchestrator.domain.model;
import lombok.Data;
import java.util.Map;
@Data
public class HttpIntegrationConfig {
    private String url;
    private String metodo;
    private Map<String, String> headers;
    private String bodyTemplate;
    private int timeout;
    private ResponseMapping mapeamentoResposta;
}
