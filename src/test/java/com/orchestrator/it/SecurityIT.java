package com.orchestrator.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test @DisplayName("Protected endpoint without token returns 401")
    void blocksWithoutToken() throws Exception {
        // The execution endpoint is the only protected one left after the refactor —
        // CRUD of flows lives in service-portal-manager.
        mockMvc.perform(post("/api/flows/any-flow/versions/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("POST /api/auth/tokens with valid credentials returns 201 + JWT")
    void createsTokenSuccessfully() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin"); req.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        LoginResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getType()).isEqualTo("Bearer");
    }

    @Test @DisplayName("POST /api/auth/tokens with invalid credentials returns 401")
    void rejectsInvalidCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("user"); req.setPassword("wrong");

        mockMvc.perform(post("/api/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("Protected endpoint accepts request with valid token")
    void acceptsWithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin"); req.setPassword("admin");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        LoginResponse resp = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);

        // Flow does not exist; auth passes so execution result returns 200 with FAILED status — never 401.
        mockMvc.perform(post("/api/flows/missing-flow/versions/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + resp.getToken()))
                .andExpect(status().isOk());
    }
}
