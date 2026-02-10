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
class WindowRunApiTest {

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
    void shouldAttachPlanDryPlanOrchestrateAndRetry() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);

        String createWindow = "{\"name\":\"RW-UT\",\"groupCode\":\"" + groupCode + "\"}";
        MvcResult rwCreate = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWindow))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        String windowId = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("id").asText();

        var it1Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-1\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"repo-1\",\"repo-2\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String it1Key = objectMapper.readTree(it1Result.getResponse().getContentAsString())
                .get("data").get("key").asText();
        var it2Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-2\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"repo-1\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String it2Key = objectMapper.readTree(it2Result.getResponse().getContentAsString())
                .get("data").get("key").asText();

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("iterationKeys", java.util.List.of(it1Key, it2Key)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value(it1Key))
            .andExpect(jsonPath("$.data[1]").value(it2Key));

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/plan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/dry-plan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());

        MvcResult orch = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/orchestrate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repoIds\":[\"repo-1\"],\"iterationKeys\":[],\"failFast\":true,\"operator\":\"tester\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists())
            .andReturn();
        String runId = objectMapper.readTree(orch.getResponse().getContentAsString()).get("data").asText();
        assertThat(runId).isNotBlank();

        mockMvc.perform(get("/api/v1/runs/" + runId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runType").value("WINDOW_ORCHESTRATION"));

        MvcResult exported = mockMvc.perform(get("/api/v1/runs/" + runId + "/export.json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode exportJson = objectMapper.readTree(exported.getResponse().getContentAsString());
        assertThat(exportJson.get("runId").asText()).isEqualTo(runId);

        String itemId = "RW-UT::repo-1::" + it1Key;
        MvcResult retry = mockMvc.perform(post("/api/v1/runs/" + runId + "/retry")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[\"" + itemId + "\"],\"operator\":\"tester2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists())
            .andReturn();
        String retryRunId = objectMapper.readTree(retry.getResponse().getContentAsString()).get("data").asText();
        assertThat(retryRunId).isNotBlank();

        mockMvc.perform(get("/api/v1/runs/" + retryRunId + "/export.json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
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
