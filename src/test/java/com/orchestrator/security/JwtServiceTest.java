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
package com.orchestrator.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(
                jwtService,
                "secret",
                "TEST_SECRET_AT_LEAST_64_CHARS_LONG_TEST_SECRET_AT_LEAST_64_CHARS_LONG");
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", 3600L);
        ReflectionTestUtils.setField(jwtService, "issuer", "test");
    }

    @Test
    @DisplayName("Gera e valida token com sucesso")
    void deveGerarEValidarToken() {
        String token = jwtService.generateToken("admin");
        assertThat(token).isNotBlank();
        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("Token inválido retorna false")
    void deveDetectarTokenInvalido() {
        assertThat(jwtService.isValid("token-invalido")).isFalse();
    }

    @Test
    @DisplayName("Token expirado retorna false")
    void deveDetectarTokenExpirado() {
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", -1L);
        String token = jwtService.generateToken("admin");
        assertThat(jwtService.isValid(token)).isFalse();
    }
}
