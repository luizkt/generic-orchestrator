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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test @DisplayName("Endpoint protegido sem token retorna 401")
    void deveBloquearSemToken() throws Exception {
        // /api/orchestrate é o único endpoint protegido restante após o
        // refactor — CRUD de flows migrou para o service-portal-manager.
        mockMvc.perform(post("/api/orchestrate/v1/qualquer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("Login com credenciais válidas retorna token JWT")
    void deveAutenticarComSucesso() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin"); req.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getType()).isEqualTo("Bearer");
    }

    @Test @DisplayName("Login com credenciais inválidas retorna 401")
    void deveRecusarCredenciaisInvalidas() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("user"); req.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("Endpoint protegido aceita requisição com token válido")
    void deveAceitarComToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin"); req.setPassword("admin");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse resp = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);

        // O fluxo não existe, mas como passou na auth o resultado de execução
        // sai como FAILED (HTTP 200 com erro embutido), nunca 401.
        mockMvc.perform(post("/api/orchestrate/v1/inexistente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + resp.getToken()))
                .andExpect(status().isOk());
    }
}
