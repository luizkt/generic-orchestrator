package com.orchestrator.service;

import com.orchestrator.domain.model.FieldType;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.InvalidFlowDefinitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlParserServiceTest {

    private YamlParserService service;

    @BeforeEach
    void setUp() { service = new YamlParserService(); }

    @Test
    @DisplayName("Deve parsear YAML válido com contrato e integrações")
    void deveParsearYamlValido() {
        String yaml = """
            fluxo:
              id: "test-flow-v1"
              descricao: "Teste"
              versao: "1.0.0"
              ativo: true
              contrato:
                campos:
                  - nome: "campo1"
                    tipo: STRING
                    obrigatorio: true
              integracoes:
                - id: "int1"
                  ordem: 1
                  tipo: HTTP
                  http:
                    url: "http://test.com"
                    metodo: GET
            """;
        FlowDefinition def = service.parse(yaml);

        assertThat(def.getFlowId()).isEqualTo("test-flow-v1");
        assertThat(def.isAtivo()).isTrue();
        assertThat(def.getContrato().getCampos()).hasSize(1);
        assertThat(def.getContrato().getCampos().get(0).getTipo()).isEqualTo(FieldType.STRING);
        assertThat(def.getIntegracoes()).hasSize(1);
        assertThat(def.getIntegracoes().get(0).getTipo()).isEqualTo(IntegrationType.HTTP);
    }

    @Test
    @DisplayName("Deve falhar quando 'fluxo' não está presente")
    void deveFalharSemChaveFluxo() {
        assertThatThrownBy(() -> service.parse("outracoisa:\n  id: x"))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("'fluxo'");
    }

    @Test
    @DisplayName("Deve falhar quando id está ausente")
    void deveFalharSemId() {
        String yaml = """
            fluxo:
              contrato:
                campos: []
              integracoes:
                - id: x
                  ordem: 1
                  tipo: HTTP
            """;
        assertThatThrownBy(() -> service.parse(yaml))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("'id'");
    }

    @Test
    @DisplayName("Deve falhar quando integrações estão vazias")
    void deveFalharSemIntegracoes() {
        String yaml = """
            fluxo:
              id: "x"
              contrato:
                campos: []
            """;
        assertThatThrownBy(() -> service.parse(yaml))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("integração");
    }

    @Test
    @DisplayName("Deve parsear YAML com provider Kafka no nível principal da integração")
    void deveParsearComKafka() {
        String yaml = """
            fluxo:
              id: "kafka-flow"
              contrato:
                campos: []
              integracoes:
                - id: "k1"
                  ordem: 1
                  tipo: QUEUE
                  provider: KAFKA
                  queue:
                    topic: "events"
            """;
        FlowDefinition d = service.parse(yaml);
        assertThat(d.getIntegracoes().get(0).getProvider().name()).isEqualTo("KAFKA");
        assertThat(d.getIntegracoes().get(0).getQueue().getTopic()).isEqualTo("events");
    }
}
