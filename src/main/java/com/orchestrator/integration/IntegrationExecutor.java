package com.orchestrator.integration;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;

public interface IntegrationExecutor {
    IntegrationType getType();
    Object execute(IntegrationDefinition definition, FlowExecutionContext context);
}
