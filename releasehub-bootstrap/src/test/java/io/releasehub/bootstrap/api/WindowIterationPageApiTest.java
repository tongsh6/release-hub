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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unitTest")
class WindowIterationPageApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    @Test
    void shouldPageWindowIterations() throws Exception {
        String token = loginAndGetToken();

        String createWindow = "{\"windowKey\":\"WK-PG\",\"name\":\"RW-PG\"}";
        var rwCreate = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWindow))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        String windowId = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("id").asText();

        String nowStart = java.time.Instant.now().minusSeconds(60).toString();
        String nowEnd = java.time.Instant.now().plusSeconds(3600).toString();
        String configReq = "{\"startAt\":\"" + nowStart + "\",\"endAt\":\"" + nowEnd + "\"}";
        mockMvc.perform(put("/api/v1/release-windows/" + windowId + "/window")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(configReq))
            .andExpect(status().isOk());

        // create 23 iterations and attach
        for (int i = 0; i < 23; i++) {
            mockMvc.perform(post("/api/v1/iterations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"iterationKey\":\"IT-PG-" + i + "\",\"description\":\"d\",\"repoIds\":[\"repo-1\"]}"))
                .andExpect(status().isOk());
        }
        String attachReq = "{\"iterationKeys\":["
                + "\"IT-PG-0\",\"IT-PG-1\",\"IT-PG-2\",\"IT-PG-3\",\"IT-PG-4\",\"IT-PG-5\",\"IT-PG-6\",\"IT-PG-7\",\"IT-PG-8\",\"IT-PG-9\","
                + "\"IT-PG-10\",\"IT-PG-11\",\"IT-PG-12\",\"IT-PG-13\",\"IT-PG-14\",\"IT-PG-15\",\"IT-PG-16\",\"IT-PG-17\",\"IT-PG-18\",\"IT-PG-19\","
                + "\"IT-PG-20\",\"IT-PG-21\",\"IT-PG-22\"]}";
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(attachReq))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations/paged?page=1&size=10")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(10))
            .andExpect(jsonPath("$.page.page").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(23))
            .andExpect(jsonPath("$.page.totalPages").value(3))
            .andExpect(jsonPath("$.page.hasNext").value(true));
    }
}
