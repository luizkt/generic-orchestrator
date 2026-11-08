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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orchestrator.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthControllerTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final AuthController controller = new AuthController(jwtService);

    AuthControllerTest() {
        ReflectionTestUtils.setField(controller, "adminUsername", "ops");
        ReflectionTestUtils.setField(controller, "adminPassword", "p@ss-ção");
        when(jwtService.generateToken("ops")).thenReturn("tok");
    }

    private LoginRequest req(String u, String p) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u);
        r.setPassword(p);
        return r;
    }

    @Test
    @DisplayName("configured credentials return 201 + Bearer token")
    void validCredentials() {
        var response = controller.createToken(req("ops", "p@ss-ção"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getToken()).isEqualTo("tok");
        assertThat(response.getBody().getType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("wrong or missing credentials return 401")
    void invalidCredentials() {
        assertThat(controller.createToken(req("ops", "x")).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.createToken(req("x", "p@ss-ção")).getStatusCode().value())
                .isEqualTo(401);
        assertThat(controller.createToken(req("ops", null)).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.createToken(req(null, null)).getStatusCode().value()).isEqualTo(401);
    }
}
