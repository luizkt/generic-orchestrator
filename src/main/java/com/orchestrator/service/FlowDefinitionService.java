package com.orchestrator.service;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.FlowNotFoundException;
import com.orchestrator.repository.FlowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDefinitionService {

    private final FlowDefinitionRepository repository;
    private final YamlParserService yamlParserService;

    public FlowDefinition save(String yaml) {
        FlowDefinition d = yamlParserService.parse(yaml);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        log.info("Salvando fluxo: {}", d.getFlowId());
        return repository.save(d);
    }

    public FlowDefinition findActiveByFlowId(String flowId) {
        return repository.findByFlowIdAndAtivoTrue(flowId)
                .orElseThrow(() -> new FlowNotFoundException("Fluxo ativo não encontrado: " + flowId));
    }

    public FlowDefinition findActiveByFlowIdAndVersion(String flowId, String versao) {
        return repository.findByFlowIdAndVersaoAndAtivoTrue(flowId, versao)
                .orElseThrow(() -> new FlowNotFoundException(
                        "Fluxo ativo não encontrado: id=" + flowId + ", versao=" + versao));
    }

    public FlowDefinition update(String flowId, String yaml) {
        FlowDefinition existing = findActiveByFlowId(flowId);
        FlowDefinition updated = yamlParserService.parse(yaml);
        updated.setMongoId(existing.getMongoId());
        updated.setCriadoEm(existing.getCriadoEm());
        updated.setAtualizadoEm(LocalDateTime.now());
        return repository.save(updated);
    }

    public List<FlowDefinition> findAllActive() {
        return repository.findByAtivoTrue();
    }

    public void deactivate(String flowId) {
        FlowDefinition d = findActiveByFlowId(flowId);
        d.setAtivo(false);
        d.setAtualizadoEm(LocalDateTime.now());
        repository.save(d);
    }
}
