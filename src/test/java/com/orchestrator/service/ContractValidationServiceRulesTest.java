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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestrator.domain.model.*;
import com.orchestrator.exception.ContractValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractValidationServiceRulesTest {

    private final ContractValidationService service = new ContractValidationService();

    private FieldDefinition field(String n, FieldType t, boolean req, ValidationRule... rules) {
        FieldDefinition f = new FieldDefinition();
        f.setName(n);
        f.setType(t);
        f.setRequired(req);
        f.setValidations(List.of(rules));
        return f;
    }

    private ValidationRule rule(ValidationType t, String value, Integer min, Integer max) {
        ValidationRule r = new ValidationRule();
        r.setType(t);
        r.setValue(value);
        r.setMin(min);
        r.setMax(max);
        return r;
    }

    private FlowContract contract(FieldDefinition... f) {
        FlowContract c = new FlowContract();
        c.setFields(List.of(f));
        return c;
    }

    private List<String> errors(FlowContract c, Map<String, Object> payload) {
        return ((ContractValidationException)
                        org.junit.jupiter.api.Assertions.assertThrows(
                                ContractValidationException.class,
                                () -> service.validate(c, payload)))
                .getErrors();
    }

    @Test
    @DisplayName("Contrato nulo ou sem campos aceita qualquer payload")
    void contratoVazioAceita() {
        assertThatCode(() -> service.validate(null, Map.of("a", 1))).doesNotThrowAnyException();
        assertThatCode(() -> service.validate(new FlowContract(), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Payload nulo com campo obrigatório gera erro")
    void payloadNuloComObrigatorio() {
        assertThat(errors(contract(field("a", FieldType.STRING, true)), null))
                .containsExactly("Field 'a' is required");
    }

    @Test
    @DisplayName("String em branco conta como ausente em campo obrigatório")
    void stringEmBrancoObrigatoria() {
        assertThat(errors(contract(field("a", FieldType.STRING, true)), Map.of("a", "  ")))
                .containsExactly("Field 'a' is required");
    }

    @Test
    @DisplayName("Valida tipos DECIMAL, BOOLEAN, OBJECT e ARRAY")
    void validaTipos() {
        FlowContract c =
                contract(
                        field("d", FieldType.DECIMAL, false),
                        field("b", FieldType.BOOLEAN, false),
                        field("o", FieldType.OBJECT, false),
                        field("l", FieldType.ARRAY, false));

        assertThatCode(
                        () ->
                                service.validate(
                                        c,
                                        Map.of("d", 1.5, "b", true, "o", Map.of(), "l", List.of())))
                .doesNotThrowAnyException();
        assertThat(errors(c, Map.of("d", "x", "b", "true", "o", "x", "l", "x")))
                .hasSize(4)
                .contains("Field 'd' should be of type DECIMAL");
    }

    @Test
    @DisplayName("Regras NOT_BLANK, NOT_EMPTY, NOT_NULL e NEGATIVE")
    void regrasSimples() {
        FlowContract c =
                contract(
                        field(
                                "s",
                                FieldType.STRING,
                                false,
                                rule(ValidationType.NOT_BLANK, null, null, null)),
                        field(
                                "l",
                                FieldType.ARRAY,
                                false,
                                rule(ValidationType.NOT_EMPTY, null, null, null)),
                        field(
                                "n",
                                FieldType.INTEGER,
                                false,
                                rule(ValidationType.NEGATIVE, null, null, null)),
                        field(
                                "z",
                                FieldType.STRING,
                                false,
                                rule(ValidationType.NOT_NULL, null, null, null)));

        assertThat(errors(c, Map.of("s", " ", "l", List.of(), "n", 1, "z", "ok"))).hasSize(3);
        assertThatCode(
                        () ->
                                service.validate(
                                        c, Map.of("s", "x", "l", List.of(1), "n", -1, "z", "ok")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Usa a mensagem customizada da regra quando informada")
    void mensagemCustomizada() {
        ValidationRule r = rule(ValidationType.POSITIVE, null, null, null);
        r.setMessage("tem que ser positivo");

        assertThat(errors(contract(field("n", FieldType.INTEGER, false, r)), Map.of("n", 0)))
                .containsExactly("tem que ser positivo");
    }

    @Test
    @DisplayName("Mensagem padrão cita o tipo da regra e o campo")
    void mensagemPadrao() {
        assertThat(
                        errors(
                                contract(
                                        field(
                                                "n",
                                                FieldType.INTEGER,
                                                false,
                                                rule(ValidationType.POSITIVE, null, null, null))),
                                Map.of("n", -2)))
                .containsExactly("Validation 'POSITIVE' failed for field 'n'");
    }

    @Test
    @DisplayName("SIZE valida strings e coleções por min e max")
    void sizeStringsEColecoes() {
        FlowContract c =
                contract(
                        field("s", FieldType.STRING, false, rule(ValidationType.SIZE, null, 2, 4)),
                        field("l", FieldType.ARRAY, false, rule(ValidationType.SIZE, null, 1, 2)));

        assertThatCode(() -> service.validate(c, Map.of("s", "abc", "l", List.of(1))))
                .doesNotThrowAnyException();
        assertThat(errors(c, Map.of("s", "a", "l", List.of(1, 2, 3)))).hasSize(2);
        assertThat(errors(c, Map.of("s", "abcde", "l", List.of()))).hasSize(2);
    }

    @Test
    @DisplayName("SIZE ignora valores que não são string nem coleção e limites ausentes")
    void sizeIgnoraOutrosTipos() {
        FlowContract c =
                contract(
                        field("n", FieldType.INTEGER, false, rule(ValidationType.SIZE, null, 5, 6)),
                        field(
                                "s",
                                FieldType.STRING,
                                false,
                                rule(ValidationType.SIZE, null, null, null)));

        assertThatCode(() -> service.validate(c, Map.of("n", 1, "s", "x")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PATTERN, MIN e MAX sem value configurado não geram erro")
    void regrasSemValue() {
        FlowContract c =
                contract(
                        field(
                                "s",
                                FieldType.STRING,
                                false,
                                rule(ValidationType.PATTERN, null, null, null)),
                        field(
                                "n",
                                FieldType.INTEGER,
                                false,
                                rule(ValidationType.MIN, null, null, null),
                                rule(ValidationType.MAX, null, null, null)));

        assertThatCode(() -> service.validate(c, Map.of("s", "x", "n", 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MAX falha acima do limite")
    void maxAcimaDoLimite() {
        FlowContract c =
                contract(
                        field(
                                "n",
                                FieldType.INTEGER,
                                false,
                                rule(ValidationType.MAX, "10", null, null)));

        assertThat(errors(c, Map.of("n", 11))).hasSize(1);
        assertThatCode(() -> service.validate(c, Map.of("n", 10))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valida campos de objeto aninhado com caminho pontuado")
    void objetoAninhado() {
        FieldDefinition address = field("address", FieldType.OBJECT, true);
        address.setObject(contract(field("city", FieldType.STRING, true)));

        assertThat(errors(contract(address), Map.of("address", Map.of())))
                .containsExactly("Field 'address.city' is required");
        assertThatCode(
                        () ->
                                service.validate(
                                        contract(address), Map.of("address", Map.of("city", "SP"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valida cada item de array de objetos com índice no caminho")
    void arrayDeObjetos() {
        FieldDefinition items = field("items", FieldType.ARRAY, true);
        items.setObject(contract(field("sku", FieldType.STRING, true)));

        assertThat(
                        errors(
                                contract(items),
                                Map.of("items", List.of(Map.of("sku", "a"), Map.of(), "texto"))))
                .containsExactly("Field 'items[1].sku' is required");
    }

    @Test
    @DisplayName("Campo sem regras de validação (null) é aceito")
    void semValidacoes() {
        FieldDefinition f = new FieldDefinition();
        f.setName("a");
        f.setType(FieldType.STRING);

        assertThatThrownBy(() -> service.validate(contract(f), Map.of("a", 1)))
                .isInstanceOf(ContractValidationException.class);
        assertThatCode(() -> service.validate(contract(f), Map.of("a", "x")))
                .doesNotThrowAnyException();
    }
}
