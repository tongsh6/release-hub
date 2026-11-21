package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 4: 发布后收尾 E2E 测试 (RM-6, QA-4, QA-5)
 *
 * <p>覆盖发布窗口关闭、收尾 Run 验证、幂等关闭、Dashboard 统计、Git 操作录制验证。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Slice4_Post_Release_Cleanup_E2ETest extends AbstractGitLabE2ETest {

    private String token;
    private String groupCode;
    private String repoId;
    private String iterKey;
    private String windowId;
    private String windowName;
    private String cleanupRunId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    // ─────────── Scenario 1: Full Setup ───────────

    @Test
    @Order(1)
    @DisplayName("[Setup] 创建分组、仓库、迭代、窗口、挂载、冻结、发布")
    void setupFullWorkflow() throws Exception {
        groupCode = createGroup(token);
        repoId = createRepo(token, groupCode);
        iterKey = createIterationWithRepo(token, groupCode, repoId);

        windowName = "TC-RW-Cleanup-" + System.currentTimeMillis();
        MvcResult winResult = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"groupCode\":\"%s\"}", windowName, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        windowId = objectMapper.readTree(winResult.getResponse().getContentAsString()).get("data").get("id").asText();

        // Attach
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());

        // Freeze
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));

        // Publish
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

    // ─────────── Scenario 2: RM 关闭窗口 ───────────

    @Test
    @Order(2)
    @DisplayName("[Release Manager] 关闭发布窗口")
    void rm_closeWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/unfreeze")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        cleanupRunId = E2ETestFixtures.closeAndReadRun(mockMvc, objectMapper, token, windowId, false).path("id").asText();
    }

    // ─────────── Scenario 3: QA 验证收尾 Run ───────────

    @Test
    @Order(3)
    @DisplayName("[Tester] 验证 Run 记录包含收尾步骤")
    void qa_verifyRunRecordWithCleanupSteps() throws Exception {
        // 按稳定 windowKey 与独立收尾类型读取本次 Run。
        MvcResult runResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", getWindowKey(token, windowId))
                        .param("runType", "WINDOW_CLEANUP")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runResult.getResponse().getContentAsString()).get("data");
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).path("id").asText()).isEqualTo(cleanupRunId);

        // 验证收尾 RunStep 序列
        verifyRunItems(cleanupRunId, 1, List.of("MERGE_TO_MASTER", "CREATE_TAG", "TRIGGER_CI"));
        var item = runPort.findById(cleanupRunId).orElseThrow().getItems().getFirst();
        assertThat(item.getIterationKey()).isNull();
        assertThat(item.getMetadata()).containsEntry("cleanup.referencePolicy", "RETAIN")
                .containsEntry("plan.triggerCi", "false");
        assertThat(item.getSteps().getLast().result().name()).isEqualTo("SKIPPED");
    }

    // ─────────── Scenario 4: RM 关闭已关闭窗口幂等 ───────────

    @Test
    @Order(4)
    @DisplayName("[Release Manager] 关闭已关闭窗口（幂等）")
    void rm_closeAlreadyClosedIdempotent() throws Exception {
        assertThat(E2ETestFixtures.closeAndReadRun(mockMvc, objectMapper, token, windowId, false).path("id").asText())
                .isEqualTo(cleanupRunId);
    }

    // ─────────── Scenario 5: QA Dashboard Stats ───────────

    @Test
    @Order(5)
    @DisplayName("[Tester] 验证 Dashboard Stats 更新")
    void qa_verifyDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRuns").isNumber())
                .andExpect(jsonPath("$.data.totalRepositories").isNumber());
    }

}
