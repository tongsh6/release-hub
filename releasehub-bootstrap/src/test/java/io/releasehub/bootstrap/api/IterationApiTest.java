package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IterationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void shouldCreateIteration() throws Exception {
        String token = loginAndGetToken();
        String req = "{\"iterationKey\":\"IT-UT-1\",\"description\":\"desc\",\"repoIds\":[\"repo-1\",\"repo-2\"]}";
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists())
            .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asText();
        assertThat(id).isEqualTo("IT-UT-1");
    }

    @Test
    void shouldCreateIterationWithNullOrMissingRepoIds() throws Exception {
        String token = loginAndGetToken();
        String reqMissing = "{\"iterationKey\":\"IT-UT-2\",\"description\":\"desc\"}";
        mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqMissing))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("IT-UT-2"));

        String reqNull = "{\"iterationKey\":\"IT-UT-3\",\"description\":\"desc\",\"repoIds\":null}";
        mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqNull))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("IT-UT-3"));
    }
}
