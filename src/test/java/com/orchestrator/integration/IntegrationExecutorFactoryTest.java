package com.orchestrator.integration;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationExecutorFactoryTest {

    private static class FakeExecutor implements IntegrationExecutor {
        private final IntegrationType type;
        FakeExecutor(IntegrationType t) { this.type = t; }
        @Override public IntegrationType getType() { return type; }
        @Override public Object execute(IntegrationDefinition d, FlowExecutionContext c) { return "ok"; }
    }

    @Test @DisplayName("Retorna o executor correto para cada tipo")
    void deveRetornarExecutorPorTipo() {
        var http = new FakeExecutor(IntegrationType.HTTP);
        var queue = new FakeExecutor(IntegrationType.QUEUE);
        IntegrationExecutorFactory factory = new IntegrationExecutorFactory(List.of(http, queue));
        assertThat(factory.get(IntegrationType.HTTP)).isSameAs(http);
        assertThat(factory.get(IntegrationType.QUEUE)).isSameAs(queue);
    }

    @Test @DisplayName("Lança exceção se executor não registrado")
    void deveLancarSeAusente() {
        IntegrationExecutorFactory factory = new IntegrationExecutorFactory(List.of());
        assertThatThrownBy(() -> factory.get(IntegrationType.HTTP))
                .isInstanceOf(IntegrationExecutionException.class);
    }
}
