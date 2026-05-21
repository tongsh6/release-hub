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
        String windowKey = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("windowKey").asText();
        String repo1 = createRepo(token, groupCode, "repo-1");

        var it1Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-1\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"" + repo1 + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String it1Key = objectMapper.readTree(it1Result.getResponse().getContentAsString())
                .get("data").get("key").asText();
        var it2Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-2\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"" + repo1 + "\"]}"))
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
            .andExpect(jsonPath("$.data[0].iterationKey").value(it1Key))
            .andExpect(jsonPath("$.data[1].iterationKey").value(it2Key));

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
                .content("{\"repoIds\":[\"" + repo1 + "\"],\"iterationKeys\":[],\"failFast\":true,\"operator\":\"tester\"}"))
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

        MvcResult windowReport = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowId").value(windowId))
            .andExpect(jsonPath("$.windowKey").value(windowKey))
            .andExpect(jsonPath("$.runCount").value(1))
            .andExpect(jsonPath("$.itemCount").value(2))
            .andExpect(jsonPath("$.runs[0].runId").value(runId))
            .andReturn();
        JsonNode reportJson = objectMapper.readTree(windowReport.getResponse().getContentAsString());
        assertThat(reportJson.get("runs").get(0).get("items")).hasSize(2);

        MvcResult windowReportCsv = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.csv")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String csv = windowReportCsv.getResponse().getContentAsString();
        assertThat(csv).contains("windowId,windowKey,runId,runType,runStatus,repo,iterationKey,finalResult,stepType,stepResult,stepStart,stepEnd,message");
        assertThat(csv).contains(windowId, windowKey, runId);

        MvcResult windowReportMarkdown = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.md")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String markdown = windowReportMarkdown.getResponse().getContentAsString();
        assertThat(markdown)
                .contains("# Release Window Report: " + windowKey)
                .contains("| Window ID | " + windowId + " |")
                .contains("## Runs")
                .contains(runId);

        String itemId = windowKey + "::" + repo1 + "::" + it1Key;
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

    private String createRepo(String token, String groupCode, String suffix) throws Exception {
        String name = "UT-" + suffix + "-" + System.currentTimeMillis();
        String req = "{" +
                "\"name\":\"" + name + "\"," +
                "\"cloneUrl\":\"https://git.example.com/" + name + ".git\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"defaultBranch\":\"main\"," +
                "\"gitProvider\":\"GITLAB\"," +
                "\"gitAccessToken\":\"test-token\"" +
                "}";
        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }
}
