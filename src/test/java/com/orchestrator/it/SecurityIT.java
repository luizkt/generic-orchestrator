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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Protected endpoint without token returns 401")
    void blocksWithoutToken() throws Exception {
        // The execution endpoint is the only protected one left after the refactor —
        // CRUD of flows lives in service-portal-manager.
        mockMvc.perform(
                        post("/api/v1/flows/any-flow/versions/v1/executions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"v2", "v3"})
    @DisplayName("v2 and v3 execution endpoints are also protected")
    void blocksV2AndV3WithoutToken(String v) throws Exception {
        mockMvc.perform(
                        post("/api/" + v + "/flows/any-flow/versions/v1/executions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/tokens with valid credentials returns 201 + JWT")
    void createsTokenSuccessfully() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin");

        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/tokens")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn();

        LoginResponse resp =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), LoginResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("POST /api/auth/tokens with invalid credentials returns 401")
    void rejectsInvalidCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("wrong");

        mockMvc.perform(
                        post("/api/auth/tokens")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /v3/api-docs is public, returns 200 without token")
    void swaggerApiDocsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoint accepts request with valid token")
    void acceptsWithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/auth/tokens")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn();

        LoginResponse resp =
                objectMapper.readValue(
                        loginResult.getResponse().getContentAsString(), LoginResponse.class);

        // Flow does not exist; auth passes so execution result returns 200 with FAILED status —
        // never 401.
        mockMvc.perform(
                        post("/api/v1/flows/missing-flow/versions/v1/executions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .header("Authorization", "Bearer " + resp.getToken()))
                .andExpect(status().isOk());
    }
}
