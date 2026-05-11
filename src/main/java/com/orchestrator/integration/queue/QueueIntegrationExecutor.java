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
    public IntegrationType getType() { return IntegrationType.QUEUE; }

    @Override
    public Object execute(IntegrationDefinition def, FlowExecutionContext ctx) {
        QueueIntegrationConfig q = def.getQueue();
        if (q == null) throw new IntegrationExecutionException("QUEUE config missing: " + def.getId());
        if (def.getProvider() == null)
            throw new IntegrationExecutionException("Queue provider not defined for: " + def.getId());

        try {
            String message = templateResolver.resolve(q.getMessageTemplate(), ctx);
            return switch (def.getProvider()) {
                case RABBITMQ -> rabbitMqPublisher.publish(def.getId(), q, message);
                case KAFKA -> kafkaPublisher.publish(def.getId(), q, message);
                case SQS -> sqsPublisher.publish(q, message);
            };
        } catch (Exception e) {
            log.error("Error QUEUE for '{}' ({}): {}", def.getId(), def.getProvider(), e.getMessage());
            throw new IntegrationExecutionException("QUEUE integration failed: " + def.getId(), e);
        }
    }
}
