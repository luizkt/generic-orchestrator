package com.orchestrator.domain.model;
import lombok.Data;
@Data
public class QueueIntegrationConfig {
    // RabbitMQ
    private String exchange;
    private String routingKey;
    private boolean persistent;
    // Kafka
    private String topic;
    private String key;
    // SQS
    private String queueUrl;
    // Common
    private String messageTemplate;
}
