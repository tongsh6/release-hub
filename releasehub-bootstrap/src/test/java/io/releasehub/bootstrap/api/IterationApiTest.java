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
            .andExpect(jsonPath("$.data.token").exists())
            .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    @Test
    void shouldCreateIteration() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        String req = "{\"name\":\"测试迭代1\",\"description\":\"desc\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"repo-1\",\"repo-2\"]}";
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andExpect(jsonPath("$.data.name").value("测试迭代1"))
            .andReturn();
        String key = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
        assertThat(key).startsWith("ITER-");
    }

    @Test
    void shouldCreateIterationWithNullOrMissingRepoIds() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        String reqMissing = "{\"name\":\"测试迭代2\",\"description\":\"desc\",\"groupCode\":\"" + groupCode + "\"}";
        mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqMissing))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andExpect(jsonPath("$.data.name").value("测试迭代2"));

        String reqNull = "{\"name\":\"测试迭代3\",\"description\":\"desc\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":null}";
        mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqNull))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andExpect(jsonPath("$.data.name").value("测试迭代3"));
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.currentTimeMillis();
        String req = "{\"name\":\"UT-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        return code;
    }
}
