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

    @Test
    @DisplayName("docs/example-flow.yml: parse e estrutura conferem com o WireMock simulado")
    void exemploFlowYamlEstaConsistenteComWiremock() throws Exception {
        // Arquivo entregue como documentação e usado no docker-compose com WireMock.
        // Garante que: (1) o YAML é parseável; (2) a URL HTTP aponta para api.exemplo.com
        //   (alias do container WireMock); (3) provider está no nível da integração
        //   (não dentro de queue); (4) integrações QUEUE têm provider definido.
        String yaml = Files.readString(Path.of("docs/example-flow.yml"));
        FlowDefinition d = service.parse(yaml);

        assertThat(d.getFlowId()).isEqualTo("criar-pedido-v1");
        assertThat(d.getVersao()).isEqualTo("1.0.0");
        assertThat(d.isAtivo()).isTrue();
        assertThat(d.getContrato().getCampos()).hasSize(2);
        assertThat(d.getIntegracoes()).hasSize(5);

        IntegrationDefinition http = d.getIntegracoes().get(0);
        assertThat(http.getTipo()).isEqualTo(IntegrationType.HTTP);
        assertThat(http.getHttp().getUrl())
                .as("URL deve apontar para o alias WireMock (HTTP, sem TLS)")
                .startsWith("http://api.exemplo.com/clientes/")
                .doesNotStartWith("https://");
        assertThat(http.getHttp().getMetodo()).isEqualTo("GET");

        IntegrationDefinition salvar = d.getIntegracoes().get(1);
        assertThat(salvar.getTipo()).isEqualTo(IntegrationType.HTTP);
        assertThat(salvar.getHttp().getMetodo()).isEqualTo("POST");
        assertThat(salvar.getHttp().getUrl()).isEqualTo("http://api.exemplo.com/pedidos");

        IntegrationDefinition rabbit = d.getIntegracoes().get(2);
        assertThat(rabbit.getProvider())
                .as("provider deve ficar no nível da integração, não dentro de queue")
                .isEqualTo(QueueProvider.RABBITMQ);
        assertThat(rabbit.getQueue().getExchange()).isEqualTo("pedidos.exchange");

        assertThat(d.getIntegracoes().get(3).getProvider()).isEqualTo(QueueProvider.KAFKA);
        assertThat(d.getIntegracoes().get(4).getProvider()).isEqualTo(QueueProvider.SQS);
    }
}
