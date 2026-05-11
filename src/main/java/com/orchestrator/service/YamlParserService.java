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
            Map<String, Object> flow = (Map<String, Object>) root.get("flow");
            if (flow == null) throw new InvalidFlowDefinitionException("YAML must contain root key 'flow'");
            String json = jsonMapper.writeValueAsString(flow);
            FlowDefinition d = jsonMapper.readValue(json, FlowDefinition.class);
            validate(d);
            return d;
        } catch (InvalidFlowDefinitionException e) { throw e; }
        catch (Exception e) {
            log.error("Failed to parse YAML", e);
            throw new InvalidFlowDefinitionException("Failed to parse definition: " + e.getMessage());
        }
    }

    private void validate(FlowDefinition d) {
        if (d.getFlowId() == null || d.getFlowId().isBlank())
            throw new InvalidFlowDefinitionException("Field 'flow.id' is required");
        if (d.getContract() == null)
            throw new InvalidFlowDefinitionException("Field 'flow.contract' is required");
        if (d.getIntegrations() == null || d.getIntegrations().isEmpty())
            throw new InvalidFlowDefinitionException("Flow must have at least one integration");
    }
}
