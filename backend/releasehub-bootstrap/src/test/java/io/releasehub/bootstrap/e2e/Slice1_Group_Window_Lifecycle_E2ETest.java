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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 1 E2E 测试：分组层级 + 发布窗口生命周期
 *
 * <p>覆盖 11 个场景（10 个用户故事）：</p>
 * <ol>
 *   <li>Admin 构建三级分组树</li>
 *   <li>Admin 验证分组树层级</li>
 *   <li>Admin 删除有子节点的分组被拒绝</li>
 *   <li>Release Manager 在非叶子分组下创建窗口被拒绝（GROUP_014）</li>
 *   <li>Release Manager 在叶子分组下创建窗口成功（DRAFT）</li>
 *   <li>Release Manager Freeze 窗口</li>
 *   <li>Release Manager 解冻窗口</li>
 *   <li>Release Manager 重新冻结后发布</li>
 *   <li>Tester 查看 window detail</li>
 *   <li>Tester 验证 Dashboard stats</li>
 *   <li>Admin 保存 GitLab 配置</li>
 * </ol>
 *
 * <p>使用本机常驻 PostgreSQL + GitLab CE（继承 AbstractGitLabE2ETest），
 * 所有数据均以时间戳前缀自给自足，不依赖其他测试类。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Slice1_Group_Window_Lifecycle_E2ETest extends AbstractGitLabE2ETest {

    private String token;

    // ── 三级分组 ──
    private String parentGroupCode;
    private String parentGroupId;
    private String childGroupCode;
    private String childGroupId;
    private String leafGroupCode;
    private String leafGroupId;

    // ── 发布窗口 ──
    private String windowId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    // ════════════════════════════ 分组层级 ════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[Admin] 构建三级分组树")
    void buildThreeLevelGroupTree() throws Exception {
        // 1a. 创建根分组 "E2E-Company"
        parentGroupCode = "TC-G-" + System.currentTimeMillis();
        MvcResult r1 = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"E2E-Company\",\"code\":\"%s\",\"parentCode\":null}",
                                parentGroupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();
        parentGroupId = objectMapper.readTree(r1.getResponse().getContentAsString())
                .get("data").asText();

        // 1b. 在根分组下创建子分组 "E2E-Team"
        childGroupCode = "TC-G-" + System.currentTimeMillis();
        MvcResult r2 = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"E2E-Team\",\"code\":\"%s\",\"parentCode\":\"%s\"}",
                                childGroupCode, parentGroupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();
        childGroupId = objectMapper.readTree(r2.getResponse().getContentAsString())
                .get("data").asText();

        // 1c. 在子分组下创建叶子分组 "E2E-Project"
        leafGroupCode = "TC-G-" + System.currentTimeMillis();
        MvcResult r3 = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"E2E-Project\",\"code\":\"%s\",\"parentCode\":\"%s\"}",
                                leafGroupCode, childGroupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();
        leafGroupId = objectMapper.readTree(r3.getResponse().getContentAsString())
                .get("data").asText();
    }

    @Test
    @Order(2)
    @DisplayName("[Admin] 验证分组树层级")
    void verifyGroupTreeHierarchy() throws Exception {
        mockMvc.perform(get("/api/v1/groups/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("[Admin] 删除有子节点的分组被拒绝")
    void deleteParentGroupWithChildrenRejected() throws Exception {
        // 尝试删除 parentGroup（它有 child 子节点），应返回 4xx
        mockMvc.perform(delete("/api/v1/groups/" + parentGroupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    // ════════════════════════════ 发布窗口生命周期 ════════════════════════════

    @Test
    @Order(4)
    @DisplayName("[Release Manager] 在非叶子分组下创建窗口被拒绝")
    void createWindowOnNonLeafGroupRejected() throws Exception {
        // childGroup 下面有 leafGroup，所以 childGroup 是非叶子节点 → GROUP_014
        mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"TC-RW-NonLeaf\",\"groupCode\":\"%s\"}",
                                childGroupCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_014"));
    }

    @Test
    @Order(5)
    @DisplayName("[Release Manager] 在叶子分组下创建窗口成功")
    void createWindowOnLeafGroupSuccess() throws Exception {
        String name = "TC-RW-" + System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"groupCode\":\"%s\"}",
                                name, leafGroupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        windowId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    @Test
    @Order(6)
    @DisplayName("[Release Manager] Freeze 窗口")
    void freezeWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));
    }

    @Test
    @Order(7)
    @DisplayName("[Release Manager] 解冻窗口")
    void unfreezeWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/unfreeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(false));
    }

    @Test
    @Order(8)
    @DisplayName("[Release Manager] 重新冻结后发布")
    void refreezeAndPublish() throws Exception {
        // 先给窗口挂一个迭代（publish 前置条件）
        String iterKey = createIteration(token, leafGroupCode);
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());

        // 重新冻结
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));

        // 发布
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());
    }

    @Test
    @Order(9)
    @DisplayName("[Tester] 查看 window detail")
    void viewWindowDetail() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(windowId))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.frozen").value(true))
                .andExpect(jsonPath("$.data.publishedAt").exists());
    }

    // ════════════════════════════ Dashboard ════════════════════════════

    @Test
    @Order(10)
    @DisplayName("[Tester] 验证 Dashboard stats")
    void verifyDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRepositories").isNumber())
                .andExpect(jsonPath("$.data.totalIterations").isNumber());
    }

    // ════════════════════════════ Settings ════════════════════════════

    @Test
    @Order(11)
    @DisplayName("[Admin] 保存 GitLab 配置")
    void saveAndGetGitlabSettings() throws Exception {
        String body = "{\"baseUrl\":\"https://gitlab.e2e-test.example.com\",\"token\":\"glpat-slice1-test-token\"}";

        mockMvc.perform(post("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // ─────────── 关闭 + 幂等 ───────────

    @Test
    @Order(12)
    @DisplayName("[Release Manager] 关闭已发布的窗口")
    void closePublishedWindow() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @Order(13)
    @DisplayName("[Release Manager] 关闭已关闭窗口 — 幂等")
    void closeAlreadyClosedWindowIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }
}
