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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsApiTest {

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
    void shouldSaveAndGetSettings() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/api/v1/settings/gitlab")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseUrl\":\"https://gitlab.example.com\",\"token\":\"abcd1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/settings/gitlab")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.baseUrl").value("https://gitlab.example.com"))
            .andExpect(jsonPath("$.data.tokenMasked").value("ab****34"));

        mockMvc.perform(post("/api/v1/settings/naming")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featureTemplate\":\"feature/%s\",\"releaseTemplate\":\"release/%s\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/settings/naming")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.featureTemplate").value("feature/%s"))
            .andExpect(jsonPath("$.data.releaseTemplate").value("release/%s"));

        mockMvc.perform(post("/api/v1/settings/ref")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/settings/ref")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists());

        mockMvc.perform(post("/api/v1/settings/blocking")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultPolicy\":\"ALLOW\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        MvcResult blockingGet = mockMvc.perform(get("/api/v1/settings/blocking")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode blocking = objectMapper.readTree(blockingGet.getResponse().getContentAsString()).get("data");
        assertThat(blocking.get("defaultPolicy").asText()).isEqualTo("ALLOW");
    }
}
