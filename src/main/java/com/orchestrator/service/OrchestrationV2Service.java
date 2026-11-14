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
package com.orchestrator.service;

import com.orchestrator.config.PlatformThreadPoolConfig;
import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * v2 — paralelismo por grupo de {@code order} com threads físicas (platform threads) num pool fixo
 * compartilhado entre todas as requests (ver {@link PlatformThreadPoolConfig}). O pool é um bean
 * singleton e nunca é fechado por request. Sob saturação vale a política do pool
 * (CallerRunsPolicy): a tarefa excedente roda na thread da request.
 */
@Service
public class OrchestrationV2Service extends AbstractParallelOrchestrationService {

    private final ExecutorService pool;

    public OrchestrationV2Service(
            WorkflowCacheService workflowCacheService,
            ContractValidationService contractValidationService,
            IntegrationExecutorFactory executorFactory,
            @Qualifier(PlatformThreadPoolConfig.BEAN_NAME) ExecutorService pool) {
        super(workflowCacheService, contractValidationService, executorFactory);
        this.pool = pool;
    }

    @Override
    protected String versionTag() {
        return "v2";
    }

    @Override
    protected ExecutorService openGroupExecutor() {
        return pool;
    }

    @Override
    protected void closeGroupExecutor(ExecutorService executor) {
        // pool compartilhado: o ciclo de vida é do Spring
    }
}
