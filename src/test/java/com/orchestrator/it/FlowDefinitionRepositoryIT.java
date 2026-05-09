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

    @Test @DisplayName("Busca fluxo por id e versao com sucesso")
    void devePersistirEBuscarPorIdEVersao() {
        FlowDefinition d = new FlowDefinition();
        d.setFlowId("versioned-flow");
        d.setVersao("2.0.0");
        d.setAtivo(true);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        repository.save(d);

        var found = repository.findByFlowIdAndVersaoAndAtivoTrue("versioned-flow", "2.0.0");
        assertThat(found).isPresent();
        assertThat(found.get().getVersao()).isEqualTo("2.0.0");
    }

    @Test @DisplayName("Nao retorna fluxo com versao diferente")
    void naoRetornaVersaoDiferente() {
        FlowDefinition d = new FlowDefinition();
        d.setFlowId("versioned-flow");
        d.setVersao("1.0.0");
        d.setAtivo(true);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        repository.save(d);

        assertThat(repository.findByFlowIdAndVersaoAndAtivoTrue("versioned-flow", "9.9.9")).isEmpty();
    }

    @Test @DisplayName("Duas versoes do mesmo fluxo coexistem e sao encontradas independentemente")
    void duasVersoesCoexistem() {
        FlowDefinition v1 = new FlowDefinition();
        v1.setFlowId("multi-version");
        v1.setVersao("1.0.0");
        v1.setAtivo(true);
        v1.setCriadoEm(LocalDateTime.now());
        v1.setAtualizadoEm(LocalDateTime.now());

        FlowDefinition v2 = new FlowDefinition();
        v2.setFlowId("multi-version");
        v2.setVersao("2.0.0");
        v2.setAtivo(true);
        v2.setCriadoEm(LocalDateTime.now());
        v2.setAtualizadoEm(LocalDateTime.now());

        repository.save(v1);
        repository.save(v2);

        assertThat(repository.findByFlowIdAndVersaoAndAtivoTrue("multi-version", "1.0.0")).isPresent();
        assertThat(repository.findByFlowIdAndVersaoAndAtivoTrue("multi-version", "2.0.0")).isPresent();
    }
}
