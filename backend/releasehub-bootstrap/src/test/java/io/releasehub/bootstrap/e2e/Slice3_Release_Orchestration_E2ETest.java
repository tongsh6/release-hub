package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 3: 发布编排 E2E 测试 (RM-5, RM-7, RM-8, QA-1, QA-5)
 *
 * <p>覆盖发布窗口的挂载迭代、冻结、发布、分支状态验证、Run 记录查询等核心编排流程。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Slice3_Release_Orchestration_E2ETest extends AbstractE2ETest {

    private String token;
    private String groupCode;
    private String repo1Id;
    private String iter1Key;
    private String window1Id;
    private String window1Name;

    // For multi-iteration multi-repo scenario
    private String window2Id;
    private String window2Name;
    private String repo2Id;
    private String iter2Key;
    private String iter3Key;

    @BeforeAll
    void authenticate() throws Exception {
        token = loginAndGetToken();
    }

    // ─────────── Scenario 1: Setup ───────────

    @Test
    @Order(1)
    @DisplayName("[Setup] 创建分组")
    void setupCreateGroup() throws Exception {
        groupCode = createGroup(token);
    }

    @Test
    @Order(2)
    @DisplayName("[Setup] 创建仓库")
    void setupCreateRepo() throws Exception {
        repo1Id = createRepo(token, groupCode);
    }

    @Test
    @Order(3)
    @DisplayName("[Setup] 创建迭代并关联仓库")
    void setupCreateIteration() throws Exception {
        iter1Key = createIterationWithRepo(token, groupCode, repo1Id);
    }

    @Test
    @Order(4)
    @DisplayName("[Setup] 创建发布窗口")
    void setupCreateWindow() throws Exception {
        window1Name = "TC-RW-" + System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"groupCode\":\"%s\"}", window1Name, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        window1Id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    // ─────────── Scenario 2: RM 挂载迭代 ───────────

    @Test
    @Order(5)
    @DisplayName("[Release Manager] 挂载迭代到发布窗口")
    void rm_attachIteration() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iter1Key)))))
                .andExpect(status().isOk());
    }

    // ─────────── Scenario 3: QA 验证挂载后迭代列表 ───────────

    @Test
    @Order(6)
    @DisplayName("[Tester] 验证挂载后迭代列表")
    void qa_verifyIterationsList() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + window1Id + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ─────────── Scenario 4: RM 冻结窗口 ───────────

    @Test
    @Order(7)
    @DisplayName("[Release Manager] 冻结发布窗口")
    void rm_freezeWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));
    }

    // ─────────── Scenario 5: RM 冻结后挂载被拒绝 ───────────

    @Test
    @Order(8)
    @DisplayName("[Release Manager] 冻结后挂载迭代被拒绝")
    void rm_attachToFrozenWindowRejected() throws Exception {
        String extraIterKey = createIteration(token, groupCode);
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(extraIterKey)))))
                .andExpect(status().is4xxClientError());
    }

    // ─────────── Scenario 6: RM 发布 ───────────

    @Test
    @Order(9)
    @DisplayName("[Release Manager] 发布窗口")
    void rm_publishWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

    // ─────────── Scenario 7: QA 验证 branch-status ───────────

    @Test
    @Order(10)
    @DisplayName("[Tester] 验证 branch-status 显示 MERGED")
    void qa_verifyBranchStatusMerged() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + window1Id + "/branch-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repos.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.repos[0].releaseBranch.mergeStatus").value("MERGED"));
    }

    // ─────────── Scenario 8: QA 验证 Run 记录已创建 (close creates cleanup run) ───────────

    @Test
    @Order(11)
    @DisplayName("[Tester] 验证 Run 记录已创建（close 触发收尾 Run）")
    void qa_verifyRunRecordCreated() throws Exception {
        // 关闭窗口触发 cleanup，创建 Run 记录
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 用 windowName 过滤（RunItem.windowKey 实际存储的是 window name）
        MvcResult runResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", window1Name)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runResult.getResponse().getContentAsString()).get("data");
        assertThat(runs.size())
                .withFailMessage("关闭后应至少存在 1 个 Run 记录")
                .isGreaterThanOrEqualTo(1);
    }

    // ─────────── Scenario 9: RM 多迭代多仓库 ───────────

    @Test
    @Order(12)
    @DisplayName("[Release Manager] 多迭代多仓库创建第二个窗口并发布 → 验证 Run")
    void rm_multiIterationMultiRepo() throws Exception {
        // 创建第二个仓库
        repo2Id = createRepo(token, groupCode);

        // 创建第二个迭代（关联仓库 2）
        iter2Key = createIterationWithRepo(token, groupCode, repo2Id);

        // 创建第三个迭代（也关联仓库 1，测试多迭代同仓库）
        iter3Key = createIterationWithRepo(token, groupCode, repo1Id);

        // 创建新发布窗口
        window2Name = "TC-RW-Multi-" + System.currentTimeMillis();
        MvcResult win2Result = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"groupCode\":\"%s\"}", window2Name, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        window2Id = objectMapper.readTree(win2Result.getResponse().getContentAsString()).get("data").get("id").asText();

        // 挂载两个迭代
        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iter2Key, iter3Key)))))
                .andExpect(status().isOk());

        // 验证迭代列表
        mockMvc.perform(get("/api/v1/release-windows/" + window2Id + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 冻结并发布
        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 关闭窗口触发 cleanup，创建 Run 记录
        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 验证 Run 存在（2 个迭代 × 各自的仓库，cleanup 会为每个迭代+仓库创建 RunItem）
        MvcResult runsResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", window2Name)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runsResult.getResponse().getContentAsString()).get("data");
        assertThat(runs.size())
                .withFailMessage("多迭代多仓库关闭后应至少存在 1 个 Run 记录")
                .isGreaterThanOrEqualTo(1);
    }

    // ─────────── Scenario 10: QA 验证发布窗口 detail ───────────

    @Test
    @Order(13)
    @DisplayName("[Tester] 验证发布窗口 detail")
    void qa_verifyWindowDetail() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + window2Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.frozen").value(true))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
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
