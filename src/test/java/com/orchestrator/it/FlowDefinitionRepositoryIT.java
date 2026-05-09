package com.orchestrator.it;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.repository.FlowDefinitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionRepositoryIT extends AbstractIntegrationTest {

    @Autowired private FlowDefinitionRepository repository;

    @AfterEach
    void cleanup() { repository.deleteAll(); }

    @Test @DisplayName("Deve persistir e recuperar fluxo no MongoDB")
    void devePersistirEBuscar() {
        FlowDefinition d = new FlowDefinition();
        d.setFlowId("integ-flow");
        d.setVersao("1.0.0");
        d.setAtivo(true);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());

        repository.save(d);

        var found = repository.findByFlowIdAndAtivoTrue("integ-flow");
        assertThat(found).isPresent();
        assertThat(found.get().getFlowId()).isEqualTo("integ-flow");
    }

    @Test @DisplayName("Não retorna fluxos inativos")
    void naoRetornaInativos() {
        FlowDefinition d = new FlowDefinition();
        d.setFlowId("inativo");
        d.setAtivo(false);
        repository.save(d);

        assertThat(repository.findByFlowIdAndAtivoTrue("inativo")).isEmpty();
    }
}
