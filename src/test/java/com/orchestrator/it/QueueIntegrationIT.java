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
package com.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.queue.KafkaPublisher;
import com.orchestrator.integration.queue.QueueIntegrationExecutor;
import com.orchestrator.integration.queue.SqsPublisher;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

class QueueIntegrationIT extends AbstractIntegrationTest {

    @Autowired private QueueIntegrationExecutor executor;
    @Autowired private KafkaPublisher kafkaPublisher;
    @Autowired private SqsPublisher sqsPublisher;
    @Autowired private SqsClient sqsClient;

    @Test
    @DisplayName("Publica mensagem no Kafka via Testcontainers")
    void devePublicarKafka() {
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setTopic("test-topic-it");

        Map<String, Object> result =
                kafkaPublisher.publish("kafka-user-tracking", cfg, "{\"hello\":\"kafka\"}");
        assertThat(result).containsEntry("provider", "KAFKA");
        assertThat(result).containsEntry("published", true);
        assertThat(result).containsKey("offset");
    }

    @Test
    @DisplayName("Publica mensagem no SQS via LocalStack")
    void devePublicarSqs() {
        var queue =
                sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-it-q").build());

        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setQueueUrl(queue.queueUrl());

        Map<String, Object> result = sqsPublisher.publish(cfg, "{\"hello\":\"sqs\"}");
        assertThat(result).containsEntry("provider", "SQS");
        assertThat(result).containsKey("messageId");

        var received =
                sqsClient.receiveMessage(
                        ReceiveMessageRequest.builder()
                                .queueUrl(queue.queueUrl())
                                .maxNumberOfMessages(1)
                                .build());
        assertThat(received.messages()).hasSize(1);
        assertThat(received.messages().get(0).body()).contains("sqs");
    }

    @Test
    @DisplayName("Executor genérico delega para Kafka quando provider = KAFKA")
    void executorDelegaKafka() {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("kafka-user-tracking");
        def.setType(IntegrationType.QUEUE);
        def.setProvider(QueueProvider.KAFKA);
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setTopic("delegated-topic");
        cfg.setMessageTemplate("{\"id\":\"{{contract.id}}\"}");
        def.setQueue(cfg);

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("id", "abc"));

        Object result = executor.execute(def, ctx);
        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("provider", "KAFKA");
    }
}
