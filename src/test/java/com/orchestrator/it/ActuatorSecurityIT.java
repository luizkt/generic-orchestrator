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
package com.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orchestrator.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureObservability
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "orchestrator.security.actuator.username=ops",
            "orchestrator.security.actuator.password=s3cret-actuator"
        })
class ActuatorSecurityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    @DisplayName("health and prometheus are public")
    void publicEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("env and loggers require authentication")
    void protectedEndpointsRejectAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/loggers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("heapdump is not exposed even with credentials")
    void heapdumpNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/heapdump").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getStatus())
                                        .as("heapdump must not be served")
                                        .isNotEqualTo(200));
    }

    @Test
    @DisplayName("ACTUATOR user with Basic credentials reads env and loggers")
    void basicCredentialsOpenProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/loggers").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong credentials are rejected")
    void wrongCredentials() throws Exception {
        mockMvc.perform(get("/actuator/env").with(httpBasic("ops", "wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").with(httpBasic("admin", "admin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a Bearer JWT does not open actuator endpoints")
    void bearerDoesNotOpenActuator() throws Exception {
        String token = jwtService.generateToken("admin");
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
