package com.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.InvalidFlowDefinitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class YamlParserService {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public YamlParserService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
        this.jsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @SuppressWarnings("unchecked")
    public FlowDefinition parse(String yamlContent) {
        try {
            Map<String, Object> root = yamlMapper.readValue(yamlContent, Map.class);
            Map<String, Object> fluxo = (Map<String, Object>) root.get("fluxo");
            if (fluxo == null) throw new InvalidFlowDefinitionException("YAML deve conter a chave raiz 'fluxo'");
            String json = jsonMapper.writeValueAsString(fluxo);
            FlowDefinition d = jsonMapper.readValue(json, FlowDefinition.class);
            validate(d);
            return d;
        } catch (InvalidFlowDefinitionException e) { throw e; }
        catch (Exception e) {
            log.error("Erro ao parsear YAML", e);
            throw new InvalidFlowDefinitionException("Erro ao parsear definição: " + e.getMessage());
        }
    }

    private void validate(FlowDefinition d) {
        if (d.getFlowId() == null || d.getFlowId().isBlank())
            throw new InvalidFlowDefinitionException("O campo 'id' do fluxo é obrigatório");
        if (d.getContrato() == null)
            throw new InvalidFlowDefinitionException("O campo 'contrato' é obrigatório");
        if (d.getIntegracoes() == null || d.getIntegracoes().isEmpty())
            throw new InvalidFlowDefinitionException("O fluxo deve ter ao menos uma integração");
    }
}
