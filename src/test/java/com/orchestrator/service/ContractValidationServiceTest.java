package com.orchestrator.service;

import com.orchestrator.domain.model.*;
import com.orchestrator.exception.ContractValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractValidationServiceTest {

    private ContractValidationService service;

    @BeforeEach
    void setUp() { service = new ContractValidationService(); }

    private FlowContract buildContract(FieldDefinition... f) {
        FlowContract c = new FlowContract();
        c.setCampos(List.of(f));
        return c;
    }

    private FieldDefinition field(String n, FieldType t, boolean req, ValidationRule... rules) {
        FieldDefinition f = new FieldDefinition();
        f.setNome(n); f.setTipo(t); f.setObrigatorio(req); f.setValidacoes(List.of(rules));
        return f;
    }

    private ValidationRule rule(ValidationType t, String v) {
        ValidationRule r = new ValidationRule();
        r.setTipo(t); r.setValor(v);
        return r;
    }

    @Test @DisplayName("Validação passa para payload válido")
    void deveValidarPayloadValido() {
        service.validate(buildContract(field("nome", FieldType.STRING, true)), Map.of("nome", "João"));
    }

    @Test @DisplayName("Falha quando campo obrigatório está ausente")
    void deveFalharCampoObrigatorioAusente() {
        assertThatThrownBy(() -> service.validate(
                buildContract(field("nome", FieldType.STRING, true)), Map.of()))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(e -> assertThat(((ContractValidationException) e).getErrors())
                        .anyMatch(s -> s.contains("nome")));
    }

    @Test @DisplayName("Falha quando tipo do campo está incorreto")
    void deveFalharTipoIncorreto() {
        assertThatThrownBy(() -> service.validate(
                buildContract(field("idade", FieldType.INTEGER, true)), Map.of("idade", "vinte")))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Validação PATTERN")
    void deveValidarPattern() {
        FlowContract c = buildContract(field("cep", FieldType.STRING, true,
                rule(ValidationType.PATTERN, "^\\d{8}$")));
        service.validate(c, Map.of("cep", "12345678"));
        assertThatThrownBy(() -> service.validate(c, Map.of("cep", "abc")))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Validação SIZE com min/max")
    void deveValidarSize() {
        ValidationRule r = new ValidationRule();
        r.setTipo(ValidationType.SIZE); r.setMin(2); r.setMax(5);
        FlowContract c = buildContract(field("nome", FieldType.STRING, true, r));
        service.validate(c, Map.of("nome", "abc"));
        assertThatThrownBy(() -> service.validate(c, Map.of("nome", "a")))
                .isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> service.validate(c, Map.of("nome", "abcdef")))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Validação POSITIVE")
    void deveValidarPositive() {
        FlowContract c = buildContract(field("v", FieldType.DECIMAL, true,
                rule(ValidationType.POSITIVE, null)));
        service.validate(c, Map.of("v", 10.5));
        assertThatThrownBy(() -> service.validate(c, Map.of("v", -1.0)))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Validação MIN/MAX numérico")
    void deveValidarMinMax() {
        FlowContract c = buildContract(field("q", FieldType.INTEGER, true,
                rule(ValidationType.MIN, "1"), rule(ValidationType.MAX, "100")));
        service.validate(c, Map.of("q", 50));
        assertThatThrownBy(() -> service.validate(c, Map.of("q", 0)))
                .isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> service.validate(c, Map.of("q", 101)))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Validação EMAIL")
    void deveValidarEmail() {
        FlowContract c = buildContract(field("e", FieldType.STRING, true, rule(ValidationType.EMAIL, null)));
        service.validate(c, Map.of("e", "test@example.com"));
        assertThatThrownBy(() -> service.validate(c, Map.of("e", "invalido")))
                .isInstanceOf(ContractValidationException.class);
    }

    @Test @DisplayName("Campos opcionais não falham quando ausentes")
    void deveAceitarCamposOpcionaisAusentes() {
        service.validate(buildContract(field("opcional", FieldType.STRING, false)), Map.of());
    }
}
