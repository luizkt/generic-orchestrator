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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMqPublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final RabbitMqPublisher publisher = new RabbitMqPublisher(Map.of("r1", template));

    private MessageDeliveryMode modeFor(QueueIntegrationConfig cfg) {
        publisher.publish("r1", cfg, "hello");
        ArgumentCaptor<MessagePostProcessor> pp =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template)
                .convertAndSend(
                        eq(cfg.getExchange() == null ? "" : cfg.getExchange()),
                        eq(cfg.getRoutingKey() == null ? "" : cfg.getRoutingKey()),
                        eq((Object) "hello"),
                        pp.capture());
        Message m = new Message(new byte[0], new MessageProperties());
        return pp.getValue().postProcessMessage(m).getMessageProperties().getDeliveryMode();
    }

    @Test
    @DisplayName("Publica com exchange/routingKey e modo persistente")
    void publicaPersistente() {
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setExchange("ex");
        cfg.setRoutingKey("rk");
        cfg.setPersistent(true);

        assertThat(modeFor(cfg)).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    @DisplayName("Usa modo não persistente quando persistent=false")
    void publicaNaoPersistente() {
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setExchange("ex");
        cfg.setRoutingKey("rk");

        assertThat(modeFor(cfg)).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
    }

    @Test
    @DisplayName("Retorna o resultado do publish e usa string vazia para exchange/rk nulos")
    void retornaMapaEDefaultsVazios() {
        Map<String, Object> out = publisher.publish("r1", new QueueIntegrationConfig(), "hello");

        verify(template)
                .convertAndSend(
                        eq(""), eq(""), eq((Object) "hello"), any(MessagePostProcessor.class));
        assertThat(out)
                .containsEntry("provider", "RABBITMQ")
                .containsEntry("integrationId", "r1")
                .containsEntry("exchange", "")
                .containsEntry("routingKey", "")
                .containsEntry("published", true);
    }

    @Test
    @DisplayName("Falha quando não há template para o integrationId")
    void falhaSemTemplate() {
        assertThatThrownBy(() -> publisher.publish("x", new QueueIntegrationConfig(), "m"))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("'x'");
    }
}
