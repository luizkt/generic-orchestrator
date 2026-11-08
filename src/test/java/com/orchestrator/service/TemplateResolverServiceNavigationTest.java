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

import com.orchestrator.domain.execution.FlowExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateResolverServiceNavigationTest {

    private final TemplateResolverService service = new TemplateResolverService();

    @Test
    @DisplayName("Navega em listas por índice numérico")
    void navegaListaPorIndice() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("items", List.of("a", "b")));

        assertThat(service.resolve("{{contract.items.1}}", ctx)).isEqualTo("b");
    }

    @Test
    @DisplayName("Índice não numérico em lista resolve para vazio")
    void indiceNaoNumerico() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("items", List.of("a")));

        assertThat(service.resolve("[{{contract.items.x}}]", ctx)).isEqualTo("[]");
    }

    @Test
    @DisplayName("Navegar além de um valor escalar resolve para vazio")
    void navegarEmEscalar() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("nome", "Ana"));

        assertThat(service.resolve("[{{contract.nome.sobrenome}}]", ctx)).isEqualTo("[]");
    }

    @Test
    @DisplayName("Raiz desconhecida resolve para vazio")
    void raizDesconhecida() {
        assertThat(service.resolve("[{{outro.campo}}]", new FlowExecutionContext()))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("Raiz nula no contexto resolve para vazio")
    void raizNula() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(null);

        assertThat(service.resolve("[{{contract.x}}]", ctx)).isEqualTo("[]");
    }

    @Test
    @DisplayName("Valor com caracteres especiais de regex é inserido literalmente")
    void valorComCaracteresEspeciais() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("v", "$1\\x"));

        assertThat(service.resolve("{{contract.v}}", ctx)).isEqualTo("$1\\x");
    }
}
