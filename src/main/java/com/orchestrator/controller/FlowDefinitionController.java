package com.orchestrator.controller;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.service.FlowDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final FlowDefinitionService service;

    @PostMapping(consumes = {"application/x-yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<FlowDefinition> create(@RequestBody String yaml) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(yaml));
    }

    @GetMapping("/{flowId}")
    public ResponseEntity<FlowDefinition> get(@PathVariable String flowId) {
        return ResponseEntity.ok(service.findActiveByFlowId(flowId));
    }

    @PutMapping(value = "/{flowId}", consumes = {"application/x-yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<FlowDefinition> update(@PathVariable String flowId, @RequestBody String yaml) {
        return ResponseEntity.ok(service.update(flowId, yaml));
    }

    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> deactivate(@PathVariable String flowId) {
        service.deactivate(flowId);
        return ResponseEntity.noContent().build();
    }
}
