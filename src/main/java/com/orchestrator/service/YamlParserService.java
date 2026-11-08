/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.orchestrator.service;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.InvalidFlowDefinitionException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Slf4j
@Service
public class YamlParserService {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public YamlParserService() {
        this.yamlMapper = YAMLMapper.builder().build();
        this.jsonMapper = JsonMapper.builder().build();
    }

    @SuppressWarnings("unchecked")
    public FlowDefinition parse(String yamlContent) {
        try {
            Map<String, Object> root = yamlMapper.readValue(yamlContent, Map.class);
            Map<String, Object> flow = (Map<String, Object>) root.get("flow");
            if (flow == null)
                throw new InvalidFlowDefinitionException("YAML must contain root key 'flow'");
            String json = jsonMapper.writeValueAsString(flow);
            FlowDefinition d = jsonMapper.readValue(json, FlowDefinition.class);
            validate(d);
            return d;
        } catch (InvalidFlowDefinitionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse YAML", e);
            throw new InvalidFlowDefinitionException(
                    "Failed to parse definition: " + e.getMessage());
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
