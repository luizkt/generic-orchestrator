package com.orchestrator.service;

import com.orchestrator.domain.execution.FlowExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateResolverServiceTest {

    private TemplateResolverService service;

    @BeforeEach
    void setUp() { service = new TemplateResolverService(); }

    @Test @DisplayName("Resolve placeholder simples do contrato")
    void deveResolverContrato() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContrato(Map.of("nome", "João"));
        assertThat(service.resolve("Olá {{contrato.nome}}!", ctx)).isEqualTo("Olá João!");
    }

    @Test @DisplayName("Resolve placeholder de integração anterior")
    void deveResolverIntegracao() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.putIntegrationResult("etapa1", Map.of("id", "ABC123"));
        assertThat(service.resolve("ID: {{integracoes.etapa1.id}}", ctx)).isEqualTo("ID: ABC123");
    }

    @Test @DisplayName("Resolve múltiplos placeholders")
    void deveResolverMultiplos() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContrato(Map.of("a", "1", "b", "2"));
        assertThat(service.resolve("{{contrato.a}}-{{contrato.b}}", ctx)).isEqualTo("1-2");
    }

    @Test @DisplayName("Resolve now()")
    void deveResolverNow() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        String r = service.resolve("ts={{now()}}", ctx);
        assertThat(r).startsWith("ts=").isNotEqualTo("ts=");
    }

    @Test @DisplayName("Retorna vazio quando placeholder não encontrado")
    void deveTratarPlaceholderInexistente() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setContrato(Map.of());
        assertThat(service.resolve("X={{contrato.inexistente}}", ctx)).isEqualTo("X=");
    }

    @Test @DisplayName("Template sem placeholders permanece igual")
    void deveManterTemplateSemPlaceholders() {
        assertThat(service.resolve("texto fixo", new FlowExecutionContext())).isEqualTo("texto fixo");
    }

    @Test @DisplayName("Aceita template nulo")
    void deveAceitarTemplateNulo() {
        assertThat(service.resolve(null, new FlowExecutionContext())).isNull();
    }
}
