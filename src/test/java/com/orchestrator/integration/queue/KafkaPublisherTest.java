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

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@SuppressWarnings("unchecked")
class KafkaPublisherTest {

    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final KafkaPublisher publisher = new KafkaPublisher(Map.of("k1", template));

    private QueueIntegrationConfig cfg(String topic) {
        QueueIntegrationConfig c = new QueueIntegrationConfig();
        c.setTopic(topic);
        c.setKey("key-1");
        return c;
    }

    @Test
    @DisplayName("Publica no topic/key configurados e retorna partição e offset")
    void publicaERetornaMetadados() {
        RecordMetadata md = new RecordMetadata(new TopicPartition("orders", 3), 0L, 42, 0L, 0, 0);
        SendResult<String, String> result = new SendResult<>(null, md);
        when(template.send("orders", "key-1", "msg"))
                .thenReturn(CompletableFuture.completedFuture(result));

        Map<String, Object> out = publisher.publish("k1", cfg("orders"), "msg");

        verify(template).send("orders", "key-1", "msg");
        assertThat(out)
                .containsEntry("provider", "KAFKA")
                .containsEntry("integrationId", "k1")
                .containsEntry("topic", "orders")
                .containsEntry("partition", 3)
                .containsEntry("offset", 42L)
                .containsEntry("published", true);
    }

    @Test
    @DisplayName("Falha quando o topic é nulo ou em branco")
    void falhaSemTopic() {
        assertThatThrownBy(() -> publisher.publish("k1", cfg(null), "m"))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("Topic Kafka");
        assertThatThrownBy(() -> publisher.publish("k1", cfg("  "), "m"))
                .isInstanceOf(IntegrationExecutionException.class);
        verifyNoInteractions(template);
    }

    @Test
    @DisplayName("Falha quando não há template para o integrationId")
    void falhaSemTemplate() {
        assertThatThrownBy(() -> publisher.publish("outro", cfg("orders"), "m"))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("'outro'");
    }

    @Test
    @DisplayName("Envolve falha do envio em IntegrationExecutionException")
    void envolveFalhaDeEnvio() {
        when(template.send("orders", "key-1", "m"))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publish("k1", cfg("orders"), "m"))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("Erro ao publicar no Kafka")
                .hasMessageContaining("broker down")
                .hasCauseInstanceOf(Exception.class);
    }
}
