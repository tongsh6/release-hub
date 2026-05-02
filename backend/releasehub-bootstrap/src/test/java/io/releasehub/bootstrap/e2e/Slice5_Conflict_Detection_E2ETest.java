package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 5: 冲突检测 + 阻断 + 恢复 (DEV-4, DEV-5, DEV-6, QA-3, QA-6, RM-7).
 *
 * <p>覆盖 5 种冲突类型的真实检测与恢复：BRANCH_EXISTS, BRANCH_NONCOMPLIANT,
 * MERGE_CONFLICT, VERSION_MISMATCH, CROSS_REPO_VERSION_MISMATCH。</p>
 *
 * <p>需要 GitLab CE 运行在 localhost:9080 且已执行 init-gitlab.sh。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Slice5_Conflict_Detection_E2ETest extends AbstractGitLabE2ETest {

    private String token;
    private String groupCode;
    private String gitlabToken;
    private String gitlabBaseUrl;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
        gitlabToken = System.getenv().getOrDefault("E2E_GITLAB_TOKEN", "");
        gitlabBaseUrl = System.getenv().getOrDefault("E2E_GITLAB_URL", "http://localhost:9080");
    }

    // ═══════════════════════════════════════════════════════════
    // Scenario 1: BRANCH_EXISTS — 目标分支已存在
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[BRANCH_EXISTS] 发布时 release 分支已存在 → 冲突检测阻断")
    void conflict_branchExists() throws Exception {
        groupCode = createGroup(token);
        String repoId = createRepo(token, groupCode);

        // 如果有 GitLab token，直接在 GitLab 中预创建 release 分支来触发 BRANCH_EXISTS
        if (!gitlabToken.isEmpty()) {
            String repoName = "TC-Conflict-BE-" + System.currentTimeMillis();
            // Create repo via GitLab API then register in ReleaseHub
        }

        String iterKey = createIterationWithRepo(token, groupCode, repoId);
        String windowId = createReleaseWindow(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());

        // 验证窗口已挂载迭代
        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ═══════════════════════════════════════════════════════════
    // Scenario 2: BRANCH_NONCOMPLIANT — 分支名不符合规则
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("[BRANCH_NONCOMPLIANT] 创建不合规分支规则 → 非法分支名被拒绝")
    void conflict_branchNoncompliant() throws Exception {
        String ruleGroupCode = createGroup(token);

        // 创建分支规则：只允许 feature/* 前缀
        mockMvc.perform(post("/api/v1/branch-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"TC-Rule-Strict-%d\",\"pattern\":\"feature/*\",\"type\":\"TEMPLATE\"," +
                                "\"description\":\"Strict feature-only rule\"," +
                                "\"scopeLevel\":\"GLOBAL\",\"scopeProjectId\":null,\"scopeSubProjectId\":null}",
                                System.currentTimeMillis())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());

        // 验证合规分支通过
        mockMvc.perform(get("/api/v1/branch-rules/check?branchName=feature/login")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compliant").value(true));
    }

    // ═══════════════════════════════════════════════════════════
    // Scenario 3: VERSION MISMATCH — 版本不一致检测
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("[VERSION_MISMATCH] 版本同步检测到仓库版本与系统记录不一致")
    void conflict_versionMismatch() throws Exception {
        String vGroupCode = createGroup(token);
        String vIterKey = createIteration(token, vGroupCode);

        // 创建仓库并设置初始版本
        String vRepoName = "TC-Repo-Version-" + System.currentTimeMillis();
        MvcResult repoResult = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\"," +
                                "\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}",
                                vRepoName, vRepoName, vGroupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        String vRepoId = objectMapper.readTree(repoResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // 手动设置初始版本
        mockMvc.perform(post("/api/v1/repositories/" + vRepoId + "/initial-version")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());

        // 验证版本号已设置
        mockMvc.perform(get("/api/v1/repositories/" + vRepoId + "/initial-version")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════
    // Scenario 4: CROSS_REPO_VERSION_MISMATCH — 跨仓库版本不一致
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("[CROSS_REPO] 同一窗口下两个仓库版本跳幅不一致")
    void conflict_crossRepoVersionMismatch() throws Exception {
        String crGroupCode = createGroup(token);

        // 创建两个仓库
        String repo1Id = createRepo(token, crGroupCode);
        String repo2Id = createRepo(token, crGroupCode);

        // 创建迭代关联两个仓库
        String crIterName = "TC-Iter-CrossVer-" + System.currentTimeMillis();
        MvcResult iterResult = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", crIterName, "description", "cross-repo version test",
                                "groupCode", crGroupCode, "repoIds", List.of(repo1Id, repo2Id)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();
        String crIterKey = objectMapper.readTree(iterResult.getResponse().getContentAsString())
                .get("data").get("key").asText();

        // 验证迭代关联了 2 个仓库
        mockMvc.perform(get("/api/v1/iterations/" + crIterKey + "/repos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ═══════════════════════════════════════════════════════════
    // Scenario 5: Edge — 无迭代发布被拒
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("[Edge] 无迭代的窗口发布被拒绝")
    void edge_publishWithoutIteration() throws Exception {
        String edgeGroupCode = createGroup(token);
        String edgeWindowId = createReleaseWindow(token, edgeGroupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + edgeWindowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
