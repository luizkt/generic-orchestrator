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

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqPublisher {

    private final Map<String, RabbitTemplate> rabbitTemplates;

    public Map<String, Object> publish(
            String integrationId, QueueIntegrationConfig cfg, String message) {
        RabbitTemplate template = rabbitTemplates.get(integrationId);
        if (template == null)
            throw new IntegrationExecutionException(
                    "No RabbitMQ configuration found for id '"
                            + integrationId
                            + "'. Check orch-integrations.rabbitmqs in application.yml");

        String exchange = cfg.getExchange() != null ? cfg.getExchange() : "";
        String rk = cfg.getRoutingKey() != null ? cfg.getRoutingKey() : "";

        log.info("[RABBITMQ] id={} exchange={} routingKey={}", integrationId, exchange, rk);
        template.convertAndSend(
                exchange,
                rk,
                message,
                m -> {
                    m.getMessageProperties()
                            .setDeliveryMode(
                                    cfg.isPersistent()
                                            ? MessageDeliveryMode.PERSISTENT
                                            : MessageDeliveryMode.NON_PERSISTENT);
                    return m;
                });
        return Map.of(
                "provider",
                "RABBITMQ",
                "integrationId",
                integrationId,
                "exchange",
                exchange,
                "routingKey",
                rk,
                "published",
                true);
    }
}
