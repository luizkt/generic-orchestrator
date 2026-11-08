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
package com.orchestrator.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertBody(
            ResponseEntity<Map<String, Object>> res, HttpStatus status, String message) {
        assertThat(res.getStatusCode()).isEqualTo(status);
        assertThat(res.getBody())
                .containsEntry("status", status.value())
                .containsEntry("error", status.getReasonPhrase())
                .containsEntry("message", message)
                .containsKey("timestamp");
    }

    @Test
    @DisplayName("FlowNotFoundException -> 404 sem details")
    void notFound() {
        var res = handler.handleNotFound(new FlowNotFoundException("flow x"));
        assertBody(res, HttpStatus.NOT_FOUND, "flow x");
        assertThat(res.getBody()).doesNotContainKey("details");
    }

    @Test
    @DisplayName("InvalidFlowDefinitionException -> 400")
    void invalidDefinition() {
        var res = handler.handleInvalid(new InvalidFlowDefinitionException("yaml ruim"));
        assertBody(res, HttpStatus.BAD_REQUEST, "yaml ruim");
    }

    @Test
    @DisplayName("ContractValidationException -> 400 com details = lista de erros")
    void contractValidation() {
        var res =
                handler.handleValidation(
                        new ContractValidationException("inválido", List.of("a", "b")));
        assertBody(res, HttpStatus.BAD_REQUEST, "inválido");
        assertThat(res.getBody()).containsEntry("details", List.of("a", "b"));
    }

    @Test
    @DisplayName("IntegrationExecutionException -> 500 com a mensagem original")
    void integration() {
        var res = handler.handleIntegration(new IntegrationExecutionException("falhou"));
        assertBody(res, HttpStatus.INTERNAL_SERVER_ERROR, "falhou");
    }

    @Test
    @DisplayName("Exception genérica -> 500 com prefixo 'Erro inesperado'")
    void generic() {
        var res = handler.handleGeneric(new IllegalStateException("oops"));
        assertBody(res, HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado: oops");
    }
}
