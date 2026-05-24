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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataQualityCleanupApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReviewSa002CleanupActionsWithoutExecutingThem() throws Exception {
        String token = loginAndGetToken();
        String body = """
                {
                  "reviewer": "qa-owner",
                  "sourceReport": ".ai/reports/sa002-safe-cleanup/manual/actions.jsonl",
                  "resourceTypeFilter": "release_window",
                  "riskTypeFilter": "DRAFT_WINDOW_REMAINS",
                  "assetScopeFilter": "HISTORICAL_ACCEPTANCE",
                  "actions": [
                    {
                      "resourceType": "release_window",
                      "resourceId": "window-1",
                      "riskType": "DRAFT_WINDOW_REMAINS",
                      "suggestedAction": "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。",
                      "executed": false,
                      "source": "release_window.status:验收窗口",
                      "dataNamespace": "acceptance",
                      "reviewBatchId": "sa002-20260523",
                      "assetScope": "HISTORICAL_ACCEPTANCE",
                      "retentionPolicy": "manual-review-then-archive",
                      "applicationEntry": "/release-windows/{resourceId}",
                      "preExecutionCheck": "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                      "postExecutionVerification": "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。",
                      "reviewDecision": "APPROVE_FOR_APPLICATION_ENTRY"
                    },
                    {
                      "resourceType": "release_window",
                      "resourceId": "window-2",
                      "riskType": "DRAFT_WINDOW_REMAINS",
                      "suggestedAction": "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。",
                      "executed": false,
                      "source": "release_window.status:验收窗口",
                      "dataNamespace": "acceptance",
                      "reviewBatchId": "sa002-20260523",
                      "assetScope": "HISTORICAL_ACCEPTANCE",
                      "retentionPolicy": "manual-review-then-archive",
                      "applicationEntry": "/release-windows/{resourceId}",
                      "preExecutionCheck": "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                      "postExecutionVerification": "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。",
                      "reviewDecision": "EXECUTE_DIRECTLY"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/data-quality/cleanup-review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.assetBoundaries[0].key").value("API_VISIBLE_ASSETS"))
                .andExpect(jsonPath("$.data.assetBoundaries[1].key").value("DB_AUDIT_ASSETS"))
                .andExpect(jsonPath("$.data.assetBoundaries[2].key").value("REVIEW_QUEUE_ACTIONS"))
                .andExpect(jsonPath("$.data.assetScopeCounts[0].assetScope").value("HISTORICAL_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.assetScopeCounts[0].count").value(2))
                .andExpect(jsonPath("$.data.actions[0].reviewStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.actions[0].dataNamespace").value("acceptance"))
                .andExpect(jsonPath("$.data.actions[0].reviewBatchId").value("sa002-20260523"))
                .andExpect(jsonPath("$.data.actions[0].assetScope").value("HISTORICAL_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.actions[0].retentionPolicy").value("manual-review-then-archive"))
                .andExpect(jsonPath("$.data.actions[0].dispositionLevel").value("APPLICATION_MANUAL"))
                .andExpect(jsonPath("$.data.actions[0].allowedAction").value("发布经理在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可走应用层删除保护。"))
                .andExpect(jsonPath("$.data.actions[0].rollbackBoundary").value("如关闭、删除或继续发布失败，保持原窗口状态，并保留复核动作重新判断。"))
                .andExpect(jsonPath("$.data.actions[0].auditRecord").value("记录 reviewer、sourceReport、windowId、业务决策、执行前状态和执行后窗口状态。"))
                .andExpect(jsonPath("$.data.actions[0].executionPermitted").value(false))
                .andExpect(jsonPath("$.data.actions[1].reviewStatus").value("REJECTED"));
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
