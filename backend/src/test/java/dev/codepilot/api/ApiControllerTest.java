package dev.codepilot.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsBackendReady() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.service").value("CodePilot AI backend"))
            .andExpect(jsonPath("$.javaVersion", not(isEmptyString())));
    }

    @Test
    void oversizedRunRequestReturnsBadRequest() throws Exception {
        String code = "x".repeat(50_001);
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(java.util.Map.of("code", code, "fileName", "Main.java"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/run")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void providerStatusEndpointListsLocalAndRemoteProviders() throws Exception {
        mockMvc.perform(get("/api/ai/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.local.configured").exists())
            .andExpect(jsonPath("$.local.url").isNotEmpty())
            .andExpect(jsonPath("$.remote.configured").exists())
            .andExpect(jsonPath("$.remote.model").isNotEmpty());
    }
}
