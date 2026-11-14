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

import com.orchestrator.integration.IntegrationExecutorFactory;
import com.orchestrator.manager.WorkflowCacheService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

/**
 * v3 — paralelismo por grupo de {@code order} com Java Virtual Threads: um executor novo por grupo,
 * uma virtual thread por tarefa, fechado ao final do grupo. Não há limite de concorrência.
 */
@Service
public class OrchestrationV3Service extends AbstractParallelOrchestrationService {

    public OrchestrationV3Service(
            WorkflowCacheService workflowCacheService,
            ContractValidationService contractValidationService,
            IntegrationExecutorFactory executorFactory) {
        super(workflowCacheService, contractValidationService, executorFactory);
    }

    @Override
    protected String versionTag() {
        return "v3";
    }

    @Override
    protected ExecutorService openGroupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    protected void closeGroupExecutor(ExecutorService executor) {
        executor.close();
    }
}
