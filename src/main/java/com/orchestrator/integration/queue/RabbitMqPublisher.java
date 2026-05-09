package com.orchestrator.integration.queue;

import com.orchestrator.domain.model.QueueIntegrationConfig;
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

    private final RabbitTemplate rabbitTemplate;

    public Map<String, Object> publish(QueueIntegrationConfig cfg, String message) {
        String exchange = cfg.getExchange() != null ? cfg.getExchange() : "";
        String rk = cfg.getRoutingKey() != null ? cfg.getRoutingKey() : "";

        log.info("[RABBITMQ] exchange={} routingKey={}", exchange, rk);
        rabbitTemplate.convertAndSend(exchange, rk, message, m -> {
            m.getMessageProperties().setDeliveryMode(
                    cfg.isPersistente() ? MessageDeliveryMode.PERSISTENT : MessageDeliveryMode.NON_PERSISTENT);
            return m;
        });
        return Map.of("provider", "RABBITMQ", "exchange", exchange, "routingKey", rk, "published", true);
    }
}
