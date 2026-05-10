package com.orchestrator.service;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final WorkflowCacheService workflowCacheService;
    private final ContractValidationService contractValidationService;
    private final IntegrationExecutorFactory executorFactory;

    public FlowExecutionResult execute(String versao, String flowId, Map<String, Object> payload) {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setExecutionId(UUID.randomUUID().toString());
        ctx.setFlowId(flowId);
        ctx.setIniciadoEm(LocalDateTime.now());
        ctx.setStatus(ExecutionStatus.RUNNING);
        ctx.setContrato(payload != null ? payload : Map.of());

        log.info("Iniciando execução [{}] do fluxo: {} versao: {}", ctx.getExecutionId(), flowId, versao);

        try {
            FlowDefinition def = workflowCacheService.load(flowId, versao);
            contractValidationService.validate(def.getContrato(), ctx.getContrato());

            boolean teveErro = false;
            for (IntegrationDefinition i : def.getIntegracoes().stream()
                    .sorted(Comparator.comparingInt(IntegrationDefinition::getOrdem)).toList()) {
                try {
                    Object result = executorFactory.get(i.getTipo()).execute(i, ctx);
                    ctx.putIntegrationResult(i.getId(), applyMapping(i, result));
                    log.info("Integração '{}' OK", i.getId());
                } catch (Exception e) {
                    if (i.isContinuarEmErro()) {
                        log.warn("Integração '{}' falhou (continuando): {}", i.getId(), e.getMessage());
                        ctx.putIntegrationResult(i.getId(), Map.of("error", e.getMessage()));
                        teveErro = true;
                    } else {
                        throw new IntegrationExecutionException(
                                "Integração obrigatória falhou: " + i.getId(), e);
                    }
                }
            }

            ctx.setStatus(teveErro ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS);
            ctx.setFinalizadoEm(LocalDateTime.now());

            return build(ctx, null);
        } catch (Exception e) {
            log.error("Erro na execução [{}]: {}", ctx.getExecutionId(), e.getMessage(), e);
            ctx.setStatus(ExecutionStatus.FAILED);
            ctx.setErrorMessage(e.getMessage());
            ctx.setFinalizadoEm(LocalDateTime.now());
            return build(ctx, e.getMessage());
        }
    }

    private FlowExecutionResult build(FlowExecutionContext ctx, String error) {
        return FlowExecutionResult.builder()
                .executionId(ctx.getExecutionId())
                .flowId(ctx.getFlowId())
                .status(ctx.getStatus())
                .resultado(ctx.getIntegracoes())
                .errorMessage(error)
                .iniciadoEm(ctx.getIniciadoEm())
                .finalizadoEm(ctx.getFinalizadoEm())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Object applyMapping(IntegrationDefinition i, Object result) {
        var mapping = switch (i.getTipo()) {
            case HTTP -> i.getHttp() != null ? i.getHttp().getMapeamentoResposta() : null;
            default -> null;
        };
        if (mapping == null || mapping.getCampoOrigem() == null) return result;
        if (result instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(mapping.getCampoOrigem());
            return Map.of(mapping.getCampoDestino() != null ? mapping.getCampoDestino() : "value",
                    v != null ? v : "");
        }
        return result;
    }
}
