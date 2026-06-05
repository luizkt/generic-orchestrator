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
package com.orchestrator.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

/**
 * Testes unitários — não validam o comportamento do {@code @Cacheable} (testado via IT separada).
 * Verificam o caminho de cache miss: chama Manager → faz parse → devolve.
 */
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
        flow.setVersion("1.0");
    }

    @Test
    @DisplayName("load: cache miss chama Manager e parser")
    void loadOk() {
        when(managerClient.fetchYaml("x", "1.0")).thenReturn("yaml-content");
        when(yamlParser.parse("yaml-content")).thenReturn(flow);

        FlowDefinition result = service.load("x", "1.0");
        assertThat(result).isSameAs(flow);
        verify(managerClient).fetchYaml("x", "1.0");
        verify(yamlParser).parse("yaml-content");
    }

    @Test
    @DisplayName("load propaga FlowNotFoundException quando Manager não acha")
    void loadNaoEncontrado() {
        when(managerClient.fetchYaml("nope", "1.0"))
                .thenThrow(new FlowNotFoundException("Não encontrado"));

        assertThatThrownBy(() -> service.load("nope", "1.0"))
                .isInstanceOf(FlowNotFoundException.class);
    }

    @Test
    @DisplayName("evict não lança e é encadeável")
    void evictOk() {
        // método anotado com @CacheEvict; o teste só garante que a chamada não quebra
        service.evict("x", "1.0");
        service.evictAll();
    }
}
