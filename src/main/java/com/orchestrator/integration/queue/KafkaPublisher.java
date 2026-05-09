package com.orchestrator.integration.queue;

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public Map<String, Object> publish(QueueIntegrationConfig cfg, String message) {
        if (cfg.getTopic() == null || cfg.getTopic().isBlank())
            throw new IntegrationExecutionException("Topic Kafka não definido");

        log.info("[KAFKA] topic={} key={}", cfg.getTopic(), cfg.getKey());
        try {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(cfg.getTopic(), cfg.getKey(), message);
            SendResult<String, String> result = future.get(10, TimeUnit.SECONDS);
            return Map.of(
                    "provider", "KAFKA",
                    "topic", cfg.getTopic(),
                    "partition", result.getRecordMetadata().partition(),
                    "offset", result.getRecordMetadata().offset(),
                    "published", true);
        } catch (Exception e) {
            throw new IntegrationExecutionException("Erro ao publicar no Kafka: " + e.getMessage(), e);
        }
    }
}
