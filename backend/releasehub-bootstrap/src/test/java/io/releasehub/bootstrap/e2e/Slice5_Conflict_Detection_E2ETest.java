package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.releasehub.application.port.out.GitBranchPort;
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
 * Slice 5: 冲突检测与重试 E2E 测试 (DEV-4, DEV-5, DEV-6, QA-3, QA-6, RM-7)
 *
 * <p>覆盖合并冲突模拟、Run 失败状态验证、重试机制、边界条件。
 * 冲突模拟通过 close 阶段的 cleanup 流程中的 MERGE_TO_MASTER 步骤触发。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Slice5_Conflict_Detection_E2ETest extends AbstractE2ETest {

    @Autowired(required = false)
    private RecordingMockGitBranchAdapter recordingAdapter;

    private String token;
    private String groupCode;
    private String repoId;
    private String iterKey;
    private String windowId;
    private String windowName;
    private String failedRunId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
        if (recordingAdapter != null) {
            recordingAdapter.clearRecords();
            recordingAdapter.resetForceOverrides();
        }
    }

    // ─────────── Scenario 1: Setup ───────────

    @Test
    @Order(1)
    @DisplayName("[Setup] 创建分组、仓库、迭代、窗口、挂载、冻结、发布")
    void setup() throws Exception {
        groupCode = createGroup(token);
        repoId = createRepo(token, groupCode);
        iterKey = createIterationWithRepo(token, groupCode, repoId);

        windowName = "TC-RW-Conflict-" + System.currentTimeMillis();
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
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    // ─────────── Scenario 2: Dev 模拟合并冲突 → close 触发 cleanup → Run FAILED ───────────

    @Test
    @Order(2)
    @DisplayName("[Developer] 模拟合并冲突 → 关闭窗口 → cleanup Run FAILED")
    void dev_simulateConflictClose_failedRun() throws Exception {
        // 注入合并冲突 — cleanup 流程的 MERGE_TO_MASTER 步骤会用到
        if (recordingAdapter != null) {
            recordingAdapter.forceMergeResult(GitBranchPort.MergeResult.conflict("Simulated merge conflict for E2E test"));
        }

        // 关闭窗口 → 触发 cleanup，其中 MERGE_TO_MASTER 会遇到冲突
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    // ─────────── Scenario 3: QA 验证 Run 失败 ───────────

    @Test
    @Order(3)
    @DisplayName("[Tester] 验证 Run 失败状态")
    void qa_verifyRunFailed() throws Exception {
        MvcResult runsResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", windowName)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        JsonNode runs = objectMapper.readTree(runsResult.getResponse().getContentAsString()).get("data");
        boolean foundFailed = false;
        for (JsonNode run : runs) {
            if ("FAILED".equals(run.get("status").asText())) {
                failedRunId = run.get("id").asText();
                foundFailed = true;
                break;
            }
        }
        assertThat(foundFailed)
                .withFailMessage("应存在状态为 FAILED 的 Run 记录")
                .isTrue();
        assertThat(failedRunId)
                .withFailMessage("failedRunId 应为非空")
                .isNotBlank();
    }

    // ─────────── Scenario 4: RM 清除冲突模拟 ───────────

    @Test
    @Order(4)
    @DisplayName("[Release Manager] 清除冲突模拟")
    void rm_resetForceOverrides() throws Exception {
        if (recordingAdapter != null) {
            recordingAdapter.resetForceOverrides();
        }
    }

    // ─────────── Scenario 5: RM 重试 ───────────

    @Test
    @Order(5)
    @DisplayName("[Release Manager] 重试失败的 Run")
    void rm_retryRun() throws Exception {
        String itemKey = windowName + "::" + repoId + "::" + iterKey;

        MvcResult retryResult = mockMvc.perform(post("/api/v1/runs/" + failedRunId + "/retry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(itemKey),
                                "operator", "tester"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        String retryRunId = objectMapper.readTree(retryResult.getResponse().getContentAsString()).get("data").asText();
        assertThat(retryRunId)
                .withFailMessage("retryRunId 应为非空")
                .isNotBlank();
    }

    // ─────────── Scenario 6: QA 验证重试后 Run SUCCESS ───────────

    @Test
    @Order(6)
    @DisplayName("[Tester] 验证重试后 Run 状态为 SUCCESS")
    void qa_verifyRetryRunSuccess() throws Exception {
        MvcResult runsResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", windowName)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runsResult.getResponse().getContentAsString()).get("data");

        // 重试后应有 SUCCESS 或 COMPLETED 状态的 Run
        boolean hasSuccessfulRun = false;
        for (JsonNode run : runs) {
            String status = run.get("status").asText();
            if ("SUCCESS".equals(status) || "COMPLETED".equals(status)) {
                hasSuccessfulRun = true;
                break;
            }
        }
        assertThat(hasSuccessfulRun)
                .withFailMessage("重试后应存在 SUCCESS 或 COMPLETED 状态的 Run")
                .isTrue();
    }

    // ─────────── Scenario 7: Edge — 无迭代发布被拒 ───────────

    @Test
    @Order(7)
    @DisplayName("[Edge] 无迭代的窗口发布被拒绝")
    void edge_publishWithoutIteration() throws Exception {
        // 新建独立窗口（不挂载迭代）
        String edgeGroupCode = createGroup(token);
        String edgeWindowId = createReleaseWindow(token, edgeGroupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + edgeWindowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    // ─── 辅助方法 ───

    private String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();
        String req = objectMapper.writeValueAsString(Map.of(
                "name", name, "description", "E2E iter",
                "groupCode", groupCode, "repoIds", List.of(repoId)));
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
    }
}
