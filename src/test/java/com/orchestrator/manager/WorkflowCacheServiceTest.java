package com.orchestrator.manager;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.FlowNotFoundException;
import com.orchestrator.service.YamlParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testes unitários — não validam o comportamento do {@code @Cacheable} (testado via IT separada).
 *  Verificam o caminho de cache miss: chama Manager → faz parse → devolve. */
@ExtendWith(MockitoExtension.class)
class WorkflowCacheServiceTest {

    @Mock private ManagerWorkflowClient managerClient;
    @Mock private YamlParserService yamlParser;

    @InjectMocks private WorkflowCacheService service;

    private FlowDefinition flow;

    @BeforeEach
    void setUp() {
        flow = new FlowDefinition();
        flow.setFlowId("x");
        flow.setVersao("1.0");
    }

    @Test @DisplayName("load: cache miss chama Manager e parser")
    void loadOk() {
        when(managerClient.fetchYaml("x", "1.0")).thenReturn("yaml-content");
        when(yamlParser.parse("yaml-content")).thenReturn(flow);

        FlowDefinition result = service.load("x", "1.0");
        assertThat(result).isSameAs(flow);
        verify(managerClient).fetchYaml("x", "1.0");
        verify(yamlParser).parse("yaml-content");
    }

    @Test @DisplayName("load propaga FlowNotFoundException quando Manager não acha")
    void loadNaoEncontrado() {
        when(managerClient.fetchYaml("nope", "1.0"))
                .thenThrow(new FlowNotFoundException("Não encontrado"));

        assertThatThrownBy(() -> service.load("nope", "1.0"))
                .isInstanceOf(FlowNotFoundException.class);
    }

    @Test @DisplayName("evict não lança e é encadeável")
    void evictOk() {
        // método anotado com @CacheEvict; o teste só garante que a chamada não quebra
        service.evict("x", "1.0");
        service.evictAll();
    }
}
