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
package com.orchestrator.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationExecutorFactoryTest {

    private static class FakeExecutor implements IntegrationExecutor {
        private final IntegrationType type;

        FakeExecutor(IntegrationType t) {
            this.type = t;
        }

        @Override
        public IntegrationType getType() {
            return type;
        }

        @Override
        public Object execute(IntegrationDefinition d, FlowExecutionContext c) {
            return "ok";
        }
    }

    @Test
    @DisplayName("Retorna o executor correto para cada tipo")
    void deveRetornarExecutorPorTipo() {
        var http = new FakeExecutor(IntegrationType.HTTP);
        var queue = new FakeExecutor(IntegrationType.QUEUE);
        IntegrationExecutorFactory factory = new IntegrationExecutorFactory(List.of(http, queue));
        assertThat(factory.get(IntegrationType.HTTP)).isSameAs(http);
        assertThat(factory.get(IntegrationType.QUEUE)).isSameAs(queue);
    }

    @Test
    @DisplayName("Lança exceção se executor não registrado")
    void deveLancarSeAusente() {
        IntegrationExecutorFactory factory = new IntegrationExecutorFactory(List.of());
        assertThatThrownBy(() -> factory.get(IntegrationType.HTTP))
                .isInstanceOf(IntegrationExecutionException.class);
    }
}
