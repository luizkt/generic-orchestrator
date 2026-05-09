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

import java.util.Map;

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
        if (q == null) throw new IntegrationExecutionException("Configuração QUEUE ausente: " + def.getId());
        if (q.getProvider() == null)
            throw new IntegrationExecutionException("Provider de fila não definido para: " + def.getId());

        try {
            String message = templateResolver.resolve(q.getMensagemTemplate(), ctx);
            return switch (q.getProvider()) {
                case RABBITMQ -> rabbitMqPublisher.publish(q, message);
                case KAFKA -> kafkaPublisher.publish(q, message);
                case SQS -> sqsPublisher.publish(q, message);
            };
        } catch (Exception e) {
            log.error("Erro QUEUE em '{}' ({}): {}", def.getId(), q.getProvider(), e.getMessage());
            throw new IntegrationExecutionException("Falha na integração QUEUE: " + def.getId(), e);
        }
    }
}
