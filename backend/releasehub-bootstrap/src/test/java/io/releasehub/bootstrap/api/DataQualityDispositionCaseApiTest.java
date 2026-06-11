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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataQualityDispositionCaseApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndTrackDispositionCaseWithoutExecutingCleanup() throws Exception {
        String token = loginAndGetToken();
        String body = """
                {
                  "requestedBy": "release-manager",
                  "sourceReport": ".ai/reports/sa002-safe-cleanup/manual/actions.jsonl",
                  "action": {
                    "resourceType": "release_window",
                    "resourceId": "window-1",
                    "riskType": "DRAFT_WINDOW_REMAINS",
                    "dataNamespace": "acceptance",
                    "reviewBatchId": "sa002-api-test",
                    "assetScope": "HISTORICAL_ACCEPTANCE",
                    "retentionPolicy": "manual-review-then-archive",
                    "reviewStatus": "ACCEPTED",
                    "reason": "已通过人工复核",
                    "applicationEntry": "/release-windows/{resourceId}",
                    "preExecutionCheck": "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                    "postExecutionVerification": "复核窗口状态已符合业务决策。",
                    "dispositionLevel": "APPLICATION_MANUAL",
                    "allowedAction": "发布经理在发布窗口页按业务判断继续发布、关闭或删除。",
                    "rollbackBoundary": "如失败，保持原窗口状态。",
                    "auditRecord": "记录 reviewer、sourceReport、windowId、业务决策。"
                  }
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/data-quality/disposition-cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.dispositionLevel").value("APPLICATION_MANUAL"))
                .andExpect(jsonPath("$.data.caseKey").exists())
                .andExpect(jsonPath("$.data.preStateSnapshot").value(""))
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = createNode.get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/data-quality/disposition-cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        mockMvc.perform(get("/api/v1/data-quality/disposition-cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id));

        mockMvc.perform(post("/api/v1/data-quality/disposition-cases/{id}/start", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operator": "release-manager",
                                  "preStateSnapshot": "{\\"status\\":\\"DRAFT\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.preStateSnapshot").value("{\"status\":\"DRAFT\"}"));

        mockMvc.perform(post("/api/v1/data-quality/disposition-cases/{id}/verify", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operator": "qa-owner",
                                  "postStateSnapshot": "{\\"status\\":\\"CLOSED\\",\\"executionPermitted\\":false}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedBy").value("qa-owner"))
                .andExpect(jsonPath("$.data.postStateSnapshot").value("{\"status\":\"CLOSED\",\"executionPermitted\":false}"));
    }

    private String loginAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }
}
