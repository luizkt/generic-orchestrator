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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPublisher {

    private final Map<String, KafkaTemplate<String, String>> kafkaTemplates;

    public Map<String, Object> publish(
            String integrationId, QueueIntegrationConfig cfg, String message) {
        if (cfg.getTopic() == null || cfg.getTopic().isBlank())
            throw new IntegrationExecutionException(
                    "Topic Kafka não definido para: " + integrationId);

        KafkaTemplate<String, String> template = kafkaTemplates.get(integrationId);
        if (template == null)
            throw new IntegrationExecutionException(
                    "Nenhuma configuração Kafka encontrada para o id '"
                            + integrationId
                            + "'. Verifique orch-integrations.kafkas no application.yml");

        log.info("[KAFKA] id={} topic={} key={}", integrationId, cfg.getTopic(), cfg.getKey());
        try {
            CompletableFuture<SendResult<String, String>> future =
                    template.send(cfg.getTopic(), cfg.getKey(), message);
            SendResult<String, String> result = future.get(10, TimeUnit.SECONDS);
            return Map.of(
                    "provider",
                    "KAFKA",
                    "integrationId",
                    integrationId,
                    "topic",
                    cfg.getTopic(),
                    "partition",
                    result.getRecordMetadata().partition(),
                    "offset",
                    result.getRecordMetadata().offset(),
                    "published",
                    true);
        } catch (Exception e) {
            throw new IntegrationExecutionException(
                    "Erro ao publicar no Kafka: " + e.getMessage(), e);
        }
    }
}
