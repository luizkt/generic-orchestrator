package com.orchestrator.integration.queue;

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqPublisher {

    private final Map<String, RabbitTemplate> rabbitTemplates;

    public Map<String, Object> publish(String integrationId, QueueIntegrationConfig cfg, String message) {
        RabbitTemplate template = rabbitTemplates.get(integrationId);
        if (template == null)
            throw new IntegrationExecutionException(
                    "Nenhuma configuração RabbitMQ encontrada para o id '" + integrationId +
                    "'. Verifique orch-integrations.rabbitmqs no application.yml");

        String exchange = cfg.getExchange() != null ? cfg.getExchange() : "";
        String rk = cfg.getRoutingKey() != null ? cfg.getRoutingKey() : "";

        log.info("[RABBITMQ] id={} exchange={} routingKey={}", integrationId, exchange, rk);
        template.convertAndSend(exchange, rk, message, m -> {
            m.getMessageProperties().setDeliveryMode(
                    cfg.isPersistente() ? MessageDeliveryMode.PERSISTENT : MessageDeliveryMode.NON_PERSISTENT);
            return m;
        });
        return Map.of("provider", "RABBITMQ", "integrationId", integrationId,
                "exchange", exchange, "routingKey", rk, "published", true);
    }
}
