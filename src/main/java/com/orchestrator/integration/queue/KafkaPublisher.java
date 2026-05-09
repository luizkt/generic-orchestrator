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

    private final Map<String, KafkaTemplate<String, String>> kafkaTemplates;

    public Map<String, Object> publish(String integrationId, QueueIntegrationConfig cfg, String message) {
        if (cfg.getTopic() == null || cfg.getTopic().isBlank())
            throw new IntegrationExecutionException("Topic Kafka não definido para: " + integrationId);

        KafkaTemplate<String, String> template = kafkaTemplates.get(integrationId);
        if (template == null)
            throw new IntegrationExecutionException(
                    "Nenhuma configuração Kafka encontrada para o id '" + integrationId +
                    "'. Verifique orch-integrations.kafkas no application.yml");

        log.info("[KAFKA] id={} topic={} key={}", integrationId, cfg.getTopic(), cfg.getKey());
        try {
            CompletableFuture<SendResult<String, String>> future =
                    template.send(cfg.getTopic(), cfg.getKey(), message);
            SendResult<String, String> result = future.get(10, TimeUnit.SECONDS);
            return Map.of(
                    "provider", "KAFKA",
                    "integrationId", integrationId,
                    "topic", cfg.getTopic(),
                    "partition", result.getRecordMetadata().partition(),
                    "offset", result.getRecordMetadata().offset(),
                    "published", true);
        } catch (Exception e) {
            throw new IntegrationExecutionException("Erro ao publicar no Kafka: " + e.getMessage(), e);
        }
    }
}
