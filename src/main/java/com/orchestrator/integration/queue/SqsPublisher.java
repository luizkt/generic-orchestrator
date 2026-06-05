package com.orchestrator.integration.queue;

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsPublisher {

    private final SqsClient sqsClient;

    public Map<String, Object> publish(QueueIntegrationConfig cfg, String message) {
        if (cfg.getQueueUrl() == null || cfg.getQueueUrl().isBlank())
            throw new IntegrationExecutionException("queueUrl SQS não definido");

        log.info("[SQS] queueUrl={}", cfg.getQueueUrl());
        SendMessageRequest req = SendMessageRequest.builder()
                .queueUrl(cfg.getQueueUrl())
                .messageBody(message)
                .build();
        SendMessageResponse res = sqsClient.sendMessage(req);
        return Map.of(
                "provider", "SQS",
                "queueUrl", cfg.getQueueUrl(),
                "messageId", res.messageId(),
                "published", true);
    }
}
