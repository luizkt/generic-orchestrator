package com.orchestrator.it;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.*;
import com.orchestrator.integration.queue.KafkaPublisher;
import com.orchestrator.integration.queue.QueueIntegrationExecutor;
import com.orchestrator.integration.queue.SqsPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueueIntegrationIT extends AbstractIntegrationTest {

    @Autowired private QueueIntegrationExecutor executor;
    @Autowired private KafkaPublisher kafkaPublisher;
    @Autowired private SqsPublisher sqsPublisher;
    @Autowired private SqsClient sqsClient;

    @Test @DisplayName("Publica mensagem no Kafka via Testcontainers")
    void devePublicarKafka() {
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setTopic("test-topic-it");

        Map<String, Object> result = kafkaPublisher.publish("kafka-user-tracking", cfg, "{\"hello\":\"kafka\"}");
        assertThat(result).containsEntry("provider", "KAFKA");
        assertThat(result).containsEntry("published", true);
        assertThat(result).containsKey("offset");
    }

    @Test @DisplayName("Publica mensagem no SQS via LocalStack")
    void devePublicarSqs() {
        var queue = sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-it-q").build());

        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setQueueUrl(queue.queueUrl());

        Map<String, Object> result = sqsPublisher.publish(cfg, "{\"hello\":\"sqs\"}");
        assertThat(result).containsEntry("provider", "SQS");
        assertThat(result).containsKey("messageId");

        var received = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queue.queueUrl()).maxNumberOfMessages(1).build());
        assertThat(received.messages()).hasSize(1);
        assertThat(received.messages().get(0).body()).contains("sqs");
    }

    @Test @DisplayName("Executor genérico delega para Kafka quando provider = KAFKA")
    void executorDelegaKafka() {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("kafka-user-tracking");
        def.setTipo(IntegrationType.QUEUE);
        def.setProvider(QueueProvider.KAFKA);
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setTopic("delegated-topic");
        cfg.setMensagemTemplate("{\"id\":\"{{contrato.id}}\"}");
        def.setQueue(cfg);

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContrato(Map.of("id", "abc"));

        Object result = executor.execute(def, ctx);
        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("provider", "KAFKA");
    }
}
