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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateResolverServiceTest {

    private TemplateResolverService service;

    @BeforeEach
    void setUp() {
        service = new TemplateResolverService();
    }

    @Test
    @DisplayName("Resolve placeholder simples do contrato")
    void deveResolverContrato() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("nome", "João"));
        assertThat(service.resolve("Olá {{contract.nome}}!", ctx)).isEqualTo("Olá João!");
    }

    @Test
    @DisplayName("Resolve placeholder de integração anterior")
    void deveResolverIntegracao() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.putIntegrationResult("etapa1", Map.of("id", "ABC123"));
        assertThat(service.resolve("ID: {{integrations.etapa1.id}}", ctx)).isEqualTo("ID: ABC123");
    }

    @Test
    @DisplayName("Resolve múltiplos placeholders")
    void deveResolverMultiplos() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of("a", "1", "b", "2"));
        assertThat(service.resolve("{{contract.a}}-{{contract.b}}", ctx)).isEqualTo("1-2");
    }

    @Test
    @DisplayName("Resolve now()")
    void deveResolverNow() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        String r = service.resolve("ts={{now()}}", ctx);
        assertThat(r).startsWith("ts=").isNotEqualTo("ts=");
    }

    @Test
    @DisplayName("Retorna vazio quando placeholder não encontrado")
    void deveTratarPlaceholderInexistente() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContract(Map.of());
        assertThat(service.resolve("X={{contract.inexistente}}", ctx)).isEqualTo("X=");
    }

    @Test
    @DisplayName("Template sem placeholders permanece igual")
    void deveManterTemplateSemPlaceholders() {
        assertThat(service.resolve("texto fixo", new FlowExecutionContext()))
                .isEqualTo("texto fixo");
    }

    @Test
    @DisplayName("Aceita template nulo")
    void deveAceitarTemplateNulo() {
        assertThat(service.resolve(null, new FlowExecutionContext())).isNull();
    }
}
