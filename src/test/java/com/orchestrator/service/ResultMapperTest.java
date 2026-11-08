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

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestrator.domain.execution.ExecutionStatus;
import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.execution.FlowExecutionResult;
import com.orchestrator.domain.model.HttpIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.domain.model.ResponseMapping;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultMapperTest {

    private IntegrationDefinition http(String source, String target) {
        IntegrationDefinition def = new IntegrationDefinition();
        def.setId("i1");
        def.setType(IntegrationType.HTTP);
        HttpIntegrationConfig cfg = new HttpIntegrationConfig();
        if (source != null || target != null) {
            ResponseMapping m = new ResponseMapping();
            m.setSourceField(source);
            m.setTargetField(target);
            cfg.setResponseMapping(m);
        }
        def.setHttp(cfg);
        return def;
    }

    @Test
    @DisplayName("applyMapping extrai o campo de origem para o campo de destino")
    void mapeiaCampo() {
        Object r = ResultMapper.applyMapping(http("a", "b"), Map.of("a", 1, "x", 2));
        assertThat(r).isEqualTo(Map.of("b", 1));
    }

    @Test
    @DisplayName("applyMapping usa 'value' sem destino e \"\" para origem ausente")
    void padroes() {
        assertThat(ResultMapper.applyMapping(http("a", null), Map.of("a", 1)))
                .isEqualTo(Map.of("value", 1));
        assertThat(ResultMapper.applyMapping(http("z", "b"), Map.of("a", 1)))
                .isEqualTo(Map.of("b", ""));
    }

    @Test
    @DisplayName("applyMapping devolve o resultado original sem mapeamento ou sem Map")
    void semMapeamento() {
        Map<String, Object> original = Map.of("a", 1);
        assertThat(ResultMapper.applyMapping(http(null, null), original)).isSameAs(original);
        assertThat(ResultMapper.applyMapping(http(null, "b"), original)).isSameAs(original);
        assertThat(ResultMapper.applyMapping(http("a", "b"), "texto")).isEqualTo("texto");

        IntegrationDefinition queue = new IntegrationDefinition();
        queue.setType(IntegrationType.QUEUE);
        assertThat(ResultMapper.applyMapping(queue, original)).isSameAs(original);

        IntegrationDefinition semHttp = new IntegrationDefinition();
        semHttp.setType(IntegrationType.HTTP);
        assertThat(ResultMapper.applyMapping(semHttp, original)).isSameAs(original);
    }

    @Test
    @DisplayName("build copia os dados do contexto para o resultado")
    void build() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setExecutionId("e1");
        ctx.setFlowId("f1");
        ctx.setStatus(ExecutionStatus.FAILED);
        ctx.setStartedAt(LocalDateTime.now());
        ctx.setFinishedAt(LocalDateTime.now());

        FlowExecutionResult r = ResultMapper.build(ctx, "erro");

        assertThat(r.getExecutionId()).isEqualTo("e1");
        assertThat(r.getFlowId()).isEqualTo("f1");
        assertThat(r.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.getErrorMessage()).isEqualTo("erro");
        assertThat(r.getStartedAt()).isEqualTo(ctx.getStartedAt());
        assertThat(r.getFinishedAt()).isEqualTo(ctx.getFinishedAt());
    }
}
