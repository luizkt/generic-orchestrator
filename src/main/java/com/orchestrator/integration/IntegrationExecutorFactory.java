package com.orchestrator.integration;

import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class IntegrationExecutorFactory {

    private final Map<IntegrationType, IntegrationExecutor> executors = new EnumMap<>(IntegrationType.class);

    public IntegrationExecutorFactory(List<IntegrationExecutor> list) {
        for (IntegrationExecutor e : list) executors.put(e.getType(), e);
    }

    public IntegrationExecutor get(IntegrationType type) {
        IntegrationExecutor e = executors.get(type);
        if (e == null) throw new IntegrationExecutionException("Executor não registrado: " + type);
        return e;
    }
}
