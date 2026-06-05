package com.orchestrator.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "TEST_SECRET_AT_LEAST_64_CHARS_LONG_TEST_SECRET_AT_LEAST_64_CHARS_LONG");
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", 3600L);
        ReflectionTestUtils.setField(jwtService, "issuer", "test");
    }

    @Test @DisplayName("Gera e valida token com sucesso")
    void deveGerarEValidarToken() {
        String token = jwtService.generateToken("admin");
        assertThat(token).isNotBlank();
        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test @DisplayName("Token inválido retorna false")
    void deveDetectarTokenInvalido() {
        assertThat(jwtService.isValid("token-invalido")).isFalse();
    }

    @Test @DisplayName("Token expirado retorna false")
    void deveDetectarTokenExpirado() {
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", -1L);
        String token = jwtService.generateToken("admin");
        assertThat(jwtService.isValid(token)).isFalse();
    }
}
