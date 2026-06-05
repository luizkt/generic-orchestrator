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

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueIntegrationExecutor implements IntegrationExecutor {

    private final TemplateResolverService templateResolver;
    private final RabbitMqPublisher rabbitMqPublisher;
    private final KafkaPublisher kafkaPublisher;
    private final SqsPublisher sqsPublisher;

    @Override
    public IntegrationType getType() {
        return IntegrationType.QUEUE;
    }

    @Override
    public Object execute(IntegrationDefinition def, FlowExecutionContext ctx) {
        QueueIntegrationConfig q = def.getQueue();
        if (q == null)
            throw new IntegrationExecutionException("QUEUE config missing: " + def.getId());
        if (def.getProvider() == null)
            throw new IntegrationExecutionException(
                    "Queue provider not defined for: " + def.getId());

        try {
            String message = templateResolver.resolve(q.getMessageTemplate(), ctx);
            return switch (def.getProvider()) {
                case RABBITMQ -> rabbitMqPublisher.publish(def.getId(), q, message);
                case KAFKA -> kafkaPublisher.publish(def.getId(), q, message);
                case SQS -> sqsPublisher.publish(q, message);
            };
        } catch (Exception e) {
            log.error(
                    "Error QUEUE for '{}' ({}): {}",
                    def.getId(),
                    def.getProvider(),
                    e.getMessage());
            throw new IntegrationExecutionException("QUEUE integration failed: " + def.getId(), e);
        }
    }
}
