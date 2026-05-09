package com.orchestrator.it;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.repository.FlowDefinitionRepository;
import com.orchestrator.service.FlowDefinitionService;
import com.orchestrator.service.OrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationFlowIT extends AbstractIntegrationTest {

    @Autowired private FlowDefinitionService flowDefinitionService;
    @Autowired private OrchestrationService orchestrationService;
    @Autowired private FlowDefinitionRepository repository;

    @AfterEach
    void cleanup() { repository.deleteAll(); }

    @Test @DisplayName("Executa fluxo completo com integração DATABASE INSERT")
    void deveExecutarFluxoComDatabase() {
        String yaml = """
            fluxo:
              id: "it-flow"
              descricao: "Integration test flow"
              versao: "1.0.0"
              ativo: true
              contrato:
                campos:
                  - nome: "nome"
                    tipo: STRING
                    obrigatorio: true
              integracoes:
                - id: "salvar"
                  ordem: 1
                  tipo: DATABASE
                  database:
                    operacao: INSERT
                    colecao: "test_collection"
                    documentoTemplate: "{\\"nome\\":\\"{{contrato.nome}}\\",\\"timestamp\\":\\"{{now()}}\\"}"
            """;

        flowDefinitionService.save(yaml);

        FlowExecutionResult result = orchestrationService.execute("it-flow", Map.of("nome", "Teste"));

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getResultado()).containsKey("salvar");
    }

    @Test @DisplayName("Falha validação de contrato propaga corretamente")
    void deveFalharValidacaoContrato() {
        String yaml = """
            fluxo:
              id: "it-flow-validation"
              ativo: true
              contrato:
                campos:
                  - nome: "obrigatorio"
                    tipo: STRING
                    obrigatorio: true
              integracoes:
                - id: "x"
                  ordem: 1
                  tipo: DATABASE
                  database:
                    operacao: INSERT
                    colecao: "x"
                    documentoTemplate: "{}"
            """;

        flowDefinitionService.save(yaml);

        FlowExecutionResult result = orchestrationService.execute("it-flow-validation", Map.of());

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("obrigatorio");
    }
}
