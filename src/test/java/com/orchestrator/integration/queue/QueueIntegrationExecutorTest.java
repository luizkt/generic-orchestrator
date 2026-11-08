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
package com.orchestrator.integration.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.domain.model.QueueProvider;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.service.TemplateResolverService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueueIntegrationExecutorTest {

    private final TemplateResolverService resolver = mock(TemplateResolverService.class);
    private final RabbitMqPublisher rabbit = mock(RabbitMqPublisher.class);
    private final KafkaPublisher kafka = mock(KafkaPublisher.class);
    private final SqsPublisher sqs = mock(SqsPublisher.class);
    private final QueueIntegrationExecutor executor =
            new QueueIntegrationExecutor(resolver, rabbit, kafka, sqs);
    private final FlowExecutionContext ctx = new FlowExecutionContext();

    private IntegrationDefinition def(QueueProvider provider, QueueIntegrationConfig q) {
        IntegrationDefinition d = new IntegrationDefinition();
        d.setId("int-1");
        d.setProvider(provider);
        d.setQueue(q);
        return d;
    }

    private QueueIntegrationConfig cfg() {
        QueueIntegrationConfig q = new QueueIntegrationConfig();
        q.setMessageTemplate("tpl");
        return q;
    }

    @Test
    @DisplayName("Tipo é QUEUE")
    void tipoQueue() {
        assertThat(executor.getType()).isEqualTo(IntegrationType.QUEUE);
    }

    @Test
    @DisplayName("Delega ao publisher conforme o provider, com a mensagem resolvida")
    void delegaPorProvider() {
        QueueIntegrationConfig q = cfg();
        when(resolver.resolve("tpl", ctx)).thenReturn("msg");
        when(rabbit.publish("int-1", q, "msg")).thenReturn(Map.of("p", "r"));
        when(kafka.publish("int-1", q, "msg")).thenReturn(Map.of("p", "k"));
        when(sqs.publish(q, "msg")).thenReturn(Map.of("p", "s"));

        assertThat(executor.execute(def(QueueProvider.RABBITMQ, q), ctx))
                .isEqualTo(Map.of("p", "r"));
        assertThat(executor.execute(def(QueueProvider.KAFKA, q), ctx)).isEqualTo(Map.of("p", "k"));
        assertThat(executor.execute(def(QueueProvider.SQS, q), ctx)).isEqualTo(Map.of("p", "s"));
        verify(rabbit).publish("int-1", q, "msg");
        verify(kafka).publish("int-1", q, "msg");
        verify(sqs).publish(q, "msg");
    }

    @Test
    @DisplayName("Falha sem config queue ou sem provider")
    void falhaSemConfigOuProvider() {
        assertThatThrownBy(() -> executor.execute(def(QueueProvider.KAFKA, null), ctx))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("QUEUE config missing");
        assertThatThrownBy(() -> executor.execute(def(null, cfg()), ctx))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("provider not defined");
        verifyNoInteractions(rabbit, kafka, sqs);
    }

    @Test
    @DisplayName("Envolve erro do publisher em IntegrationExecutionException preservando a causa")
    void envolveErroDoPublisher() {
        QueueIntegrationConfig q = cfg();
        when(resolver.resolve("tpl", ctx)).thenReturn("msg");
        RuntimeException boom = new RuntimeException("boom");
        when(kafka.publish("int-1", q, "msg")).thenThrow(boom);

        assertThatThrownBy(() -> executor.execute(def(QueueProvider.KAFKA, q), ctx))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("QUEUE integration failed: int-1")
                .hasCause(boom);
    }
}
