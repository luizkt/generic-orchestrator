package com.orchestrator.service;

import com.orchestrator.domain.model.FieldType;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.domain.model.QueueProvider;
import com.orchestrator.exception.InvalidFlowDefinitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlParserServiceTest {

    private YamlParserService service;

    @BeforeEach
    void setUp() { service = new YamlParserService(); }

    @Test
    @DisplayName("Parses a valid YAML with contract and integrations")
    void parsesValidYaml() {
        String yaml = """
            flow:
              id: "test-flow-v1"
              description: "Test"
              version: "1.0.0"
              active: true
              contract:
                fields:
                  - name: "field1"
                    type: STRING
                    required: true
              integrations:
                - id: "int1"
                  order: 1
                  type: HTTP
                  http:
                    url: "http://test.com"
                    method: GET
            """;
        FlowDefinition def = service.parse(yaml);

        assertThat(def.getFlowId()).isEqualTo("test-flow-v1");
        assertThat(def.isActive()).isTrue();
        assertThat(def.getContract().getFields()).hasSize(1);
        assertThat(def.getContract().getFields().get(0).getType()).isEqualTo(FieldType.STRING);
        assertThat(def.getIntegrations()).hasSize(1);
        assertThat(def.getIntegrations().get(0).getType()).isEqualTo(IntegrationType.HTTP);
    }

    @Test
    @DisplayName("Fails when 'flow' root key is missing")
    void failsWithoutFlowKey() {
        assertThatThrownBy(() -> service.parse("other:\n  id: x"))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("'flow'");
    }

    @Test
    @DisplayName("Fails when id is missing")
    void failsWithoutId() {
        String yaml = """
            flow:
              contract:
                fields: []
              integrations:
                - id: x
                  order: 1
                  type: HTTP
            """;
        assertThatThrownBy(() -> service.parse(yaml))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("'flow.id'");
    }

    @Test
    @DisplayName("Fails when integrations is empty")
    void failsWithoutIntegrations() {
        String yaml = """
            flow:
              id: "x"
              contract:
                fields: []
            """;
        assertThatThrownBy(() -> service.parse(yaml))
                .isInstanceOf(InvalidFlowDefinitionException.class)
                .hasMessageContaining("integration");
    }

    @Test
    @DisplayName("Parses YAML with Kafka provider at integration level")
    void parsesWithKafka() {
        String yaml = """
            flow:
              id: "kafka-flow"
              contract:
                fields: []
              integrations:
                - id: "k1"
                  order: 1
                  type: QUEUE
                  provider: KAFKA
                  queue:
                    topic: "events"
            """;
        FlowDefinition d = service.parse(yaml);
        assertThat(d.getIntegrations().get(0).getProvider().name()).isEqualTo("KAFKA");
        assertThat(d.getIntegrations().get(0).getQueue().getTopic()).isEqualTo("events");
    }

    @Test
    @DisplayName("Parses YAML with optional validations section")
    void parsesWithValidations() {
        String yaml = """
            flow:
              id: "flow-with-validations"
              contract:
                fields: []
              integrations:
                - id: "step-1"
                  order: 1
                  type: HTTP
                  http:
                    url: "http://api.test/data"
                    method: GET
              validations:
                - id: "check-credit"
                  order: 1
                  type: HTTP
                  http:
                    url: "http://api.test/credit"
                    method: GET
            """;
        FlowDefinition d = service.parse(yaml);

        assertThat(d.getIntegrations()).hasSize(1);
        assertThat(d.getValidations()).hasSize(1);
        assertThat(d.getValidations().get(0).getId()).isEqualTo("check-credit");
        assertThat(d.getValidations().get(0).getType()).isEqualTo(IntegrationType.HTTP);
    }

    @Test
    @DisplayName("docs/example-flow.yml: parse and structure match the WireMock setup")
    void exampleFlowYamlIsConsistentWithWiremock() throws Exception {
        // Garante que: (1) o YAML é parseável; (2) a URL HTTP aponta para api.exemplo.com
        //   (alias do container WireMock); (3) provider está no nível da integração
        //   (não dentro de queue); (4) integrações QUEUE têm provider definido.
        String yaml = Files.readString(Path.of("docs/example-flow.yml"));
        FlowDefinition d = service.parse(yaml);

        assertThat(d.getFlowId()).isEqualTo("create-order-v1");
        assertThat(d.getVersion()).isEqualTo("1.0.0");
        assertThat(d.isActive()).isTrue();
        assertThat(d.getContract().getFields()).hasSize(2);
        assertThat(d.getIntegrations()).hasSize(5);

        IntegrationDefinition http = d.getIntegrations().get(0);
        assertThat(http.getType()).isEqualTo(IntegrationType.HTTP);
        assertThat(http.getHttp().getUrl())
                .as("URL must point to WireMock alias (HTTP, no TLS)")
                .startsWith("http://api.exemplo.com/clients/")
                .doesNotStartWith("https://");
        assertThat(http.getHttp().getMethod()).isEqualTo("GET");

        IntegrationDefinition save = d.getIntegrations().get(1);
        assertThat(save.getType()).isEqualTo(IntegrationType.HTTP);
        assertThat(save.getHttp().getMethod()).isEqualTo("POST");
        assertThat(save.getHttp().getUrl()).isEqualTo("http://api.exemplo.com/orders");

        IntegrationDefinition rabbit = d.getIntegrations().get(2);
        assertThat(rabbit.getProvider())
                .as("provider must be at integration level, not inside queue")
                .isEqualTo(QueueProvider.RABBITMQ);
        assertThat(rabbit.getQueue().getExchange()).isEqualTo("orders.exchange");

        assertThat(d.getIntegrations().get(3).getProvider()).isEqualTo(QueueProvider.KAFKA);
        assertThat(d.getIntegrations().get(4).getProvider()).isEqualTo(QueueProvider.SQS);
    }
}
