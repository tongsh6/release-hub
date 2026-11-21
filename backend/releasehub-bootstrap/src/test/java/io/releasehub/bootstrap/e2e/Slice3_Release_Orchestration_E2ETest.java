package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class Slice3_Release_Orchestration_E2ETest extends AbstractGitLabE2ETest {

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
    @DisplayName("[Tester] 验证 branch-status 仅展示分支事实")
    void qa_verifyBranchStatusRefs() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + window1Id + "/branch-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repos.length()").value(1))
                .andExpect(jsonPath("$.data.repos[0].repoId").value(repo1Id))
                .andExpect(jsonPath("$.data.repos[0].iterationKey").value(iter1Key))
                .andExpect(jsonPath("$.data.repos[0].featureBranch.exists").value(false))
                .andExpect(jsonPath("$.data.repos[0].releaseBranch.exists").value(false))
                .andExpect(jsonPath("$.data.repos[0].releaseBranch.mergeStatus").doesNotExist());
    }

    // ─────────── Scenario 8: QA 验证 Run 记录已创建 (close creates cleanup run) ───────────

    @Test
    @Order(11)
    @DisplayName("[Tester] 显式编排、原回执重放和收尾均有真实 MR 成功证据")
    void qa_verifyRunRecordCreated() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + window1Id + "/unfreeze")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        prepareFeatureChanges(window1Id, List.of(repo1Id), List.of(iter1Key));
        orchestrateAndVerify(window1Id, List.of(repo1Id), List.of(iter1Key));
        JsonNode cleanup = E2ETestFixtures.closeAndReadRun(mockMvc, objectMapper, token, window1Id, false);
        verifySuccessfulCleanup(cleanup);

        // 按窗口稳定 key 和独立收尾类型读取，避免混入集成 Run。
        MvcResult runResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", getWindowKey(token, window1Id))
                        .param("runType", "WINDOW_CLEANUP")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runResult.getResponse().getContentAsString()).get("data");
        assertThat(runs.size())
                .withFailMessage("关闭后应至少存在 1 个 Run 记录")
                .isGreaterThanOrEqualTo(1);

        // 验证 RunItem 和 RunStep 内容
        String runId = cleanup.path("id").asText();
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).path("id").asText()).isEqualTo(runId);
        verifyRunItems(runId, 1, java.util.List.of("MERGE_TO_MASTER", "CREATE_TAG", "TRIGGER_CI"));
    }

    // ─────────── Scenario 9: RM 多迭代多仓库 ───────────

    @Test
    @Order(12)
    @DisplayName("[Release Manager] 多迭代多仓库创建第二个窗口并发布 → 验证 Run")
    void rm_multiIterationMultiRepo() throws Exception {
        // 本场景使用两个新的隔离仓库，不复用前一窗口已发布的版本 Tag。
        repo2Id = createRepo(token, groupCode);
        String sharedRepoId = createRepo(token, groupCode);

        iter2Key = createIterationWithRepo(token, groupCode, repo2Id);

        // 两个迭代共享同一仓库，仍真实验证其 feature 到同一 release 的串行合并。
        iter3Key = createIterationWithRepo(token, groupCode, sharedRepoId);
        String iter4Key = createIterationWithRepo(token, groupCode, sharedRepoId);

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

        // 挂载三个迭代，仓库并集为两个真实 GitLab 项目。
        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iter2Key, iter3Key, iter4Key)))))
                .andExpect(status().isOk());

        // 验证迭代列表
        mockMvc.perform(get("/api/v1/release-windows/" + window2Id + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

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

        mockMvc.perform(post("/api/v1/release-windows/" + window2Id + "/unfreeze")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        List<String> repos = List.of(repo2Id, sharedRepoId);
        List<String> selectedIterations = List.of(iter2Key, iter3Key, iter4Key);
        prepareFeatureChanges(window2Id, repos, selectedIterations);
        orchestrateAndVerify(window2Id, repos, selectedIterations);
        JsonNode cleanup = E2ETestFixtures.closeAndReadRun(mockMvc, objectMapper, token, window2Id, false);
        verifySuccessfulCleanup(cleanup);
        assertThat(cleanup.path("items")).hasSize(2);

        // 收尾按仓库并集执行，每仓库一个 Item，参与迭代保存在计划中。
        MvcResult runsResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", getWindowKey(token, window2Id))
                        .param("runType", "WINDOW_CLEANUP")
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


    /**
     * 只准备 feature 上的业务差异。release 与 MR 必须由真实 Orchestrate 首次创建。
     * 分支来源取不可变预览，测试不自行拼接 feature/release 命名。
     */
    private void prepareFeatureChanges(String windowId, List<String> repoIds, List<String> iterations) throws Exception {
        MvcResult previewResponse = mockMvc.perform(post("/api/v1/release-windows/{id}/run-plans", windowId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "slice3-preview-" + windowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scopeBody(repoIds, iterations)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED")).andReturn();
        JsonNode plan = objectMapper.readTree(previewResponse.getResponse().getContentAsString()).path("data").path("snapshot");
        assertThat(plan.path("items")).hasSize(iterations.size());
        for (JsonNode item : plan.path("items")) {
            String repoId = item.path("repoId").asText();
            String feature = item.path("featureBranch").asText();
            String release = item.path("releaseBranch").asText();
            String base = item.path("defaultBranch").asText();
            assertThat(feature).isNotBlank();
            assertThat(release).isNotBlank();
            assertThat(base).isNotBlank();
            assertThatThrownBy(() -> gitLabRequest(repoId, HttpMethod.GET,
                    "/repository/branches/" + encode(feature), null))
                    .as("配置/预览不能创建 feature")
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
            assertThatThrownBy(() -> gitLabRequest(repoId, HttpMethod.GET,
                    "/repository/branches/" + encode(release), null))
                    .as("配置/预览不能创建 release")
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
            JsonNode commit = gitLabRequest(repoId, HttpMethod.POST, "/repository/commits", Map.of(
                    "branch", feature, "start_branch", base,
                    "commit_message", "E2E: prepare independent iteration change",
                    "actions", List.of(Map.of("action", "create",
                            "file_path", "e2e-" + UUID.randomUUID() + ".txt",
                            "content", plan.path("windowKey").asText() + "/" + item.path("iterationKey").asText()))));
            assertThat(commit.path("id").asText()).as("feature fixture 必须有真实新提交").isNotBlank();
        }
    }

    private Run orchestrateAndVerify(String windowId, List<String> repoIds, List<String> iterations) throws Exception {
        String key = "slice3-orchestrate-" + windowId;
        String body = scopeBody(repoIds, iterations);
        MvcResult response = mockMvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String runId = objectMapper.readTree(response.getResponse().getContentAsString()).path("data").asText();
        assertThat(runId).isNotBlank();
        Run run = runPort.findById(runId).orElseThrow();
        assertThat(run.getExecutionStatus().name())
                .withFailMessage("真实编排必须 DONE，实际为 %s；步骤证据：%s",
                        run.getExecutionStatus(), run.getItems().stream().flatMap(item -> item.getSteps().stream()).toList())
                .isEqualTo("DONE");
        assertThat(run.getItems()).hasSize(iterations.size());
        verifyRunItems(runId, iterations.size(),
                List.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE"));
        Map<String, Instant> previousRepoEnd = new HashMap<>();
        Set<String> ensuredRepositories = new HashSet<>();
        for (RunItem item : run.getItems().stream().sorted(Comparator.comparingInt(RunItem::getPlannedOrder)).toList()) {
            assertThat(item.getFinalResult().name()).isIn("MERGED", "ALREADY_MERGED");
            assertThat(item.getExecutedOrder()).isPositive();
            assertThat(item.getSteps().get(0).result().name()).isEqualTo("ALREADY_EXISTS");
            assertThat(item.getSteps().get(1).result().name())
                    .isEqualTo(ensuredRepositories.add(item.getRepo().value()) ? "CREATED" : "ALREADY_EXISTS");
            assertThat(item.getSteps().get(2).result().name()).isEqualTo("CREATED");
            assertThat(item.getSteps().get(3).result().name()).isIn("MERGED", "ALREADY_MERGED");
            Instant earlier = previousRepoEnd.put(item.getRepo().value(), item.getSteps().get(3).endAt());
            if (earlier != null) assertThat(item.getSteps().get(0).startAt()).isAfterOrEqualTo(earlier);
            verifyMergedEvidence(item.getRepo().value(), item.getMetadata());
            assertThat(pairMergeRequests(item.getRepo().value(), item.getMetadata())).hasSize(1);
        }

        // 同一个真实请求回执不得创建新 Run、Step 或 MR。
        long originalVersion = run.getVersion();
        mockMvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(runId));
        Run replayed = runPort.findById(runId).orElseThrow();
        assertThat(replayed.getVersion()).isEqualTo(originalVersion);
        for (RunItem item : replayed.getItems()) {
            JsonNode mergeRequests = pairMergeRequests(item.getRepo().value(), item.getMetadata());
            assertThat(mergeRequests).hasSize(1);
            assertThat(mergeRequests.get(0).path("iid").asText()).isEqualTo(item.getMetadata().get("git.mrIid"));
        }
        MvcResult refsResponse = mockMvc.perform(get("/api/v1/release-windows/{id}/branch-status", windowId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        JsonNode refs = objectMapper.readTree(refsResponse.getResponse().getContentAsString()).path("data").path("repos");
        assertThat(refs).hasSize(iterations.size());
        for (JsonNode ref : refs) {
            assertThat(ref.path("featureBranch").path("exists").asBoolean()).isTrue();
            assertThat(ref.path("releaseBranch").path("exists").asBoolean()).isTrue();
            assertThat(ref.path("releaseBranch").has("mergeStatus")).isFalse();
        }
        return run;
    }

    private void verifySuccessfulCleanup(JsonNode cleanup) {
        assertThat(cleanup.path("status").asText())
                .withFailMessage("收尾必须真实成功，不能以步骤存在替代合并证据：%s", cleanup).isEqualTo("DONE");
        Run run = runPort.findById(cleanup.path("id").asText()).orElseThrow();
        for (RunItem item : run.getItems()) {
            assertThat(item.getFinalResult().name()).isEqualTo("SUCCESS");
            assertThat(item.getSteps().get(0).result().name()).isIn("MERGED", "ALREADY_MERGED");
            assertThat(item.getSteps().get(2).result().name()).isEqualTo("SKIPPED");
            assertThat(item.getMetadata().get("cleanup.referencePolicy")).isEqualTo("RETAIN");
            verifyMergedEvidence(item.getRepo().value(), item.getMetadata());
            assertThat(pairMergeRequests(item.getRepo().value(), item.getMetadata())).hasSize(1);
            String tag = item.getMetadata().get("plan.tagName");
            if (tag == null || tag.isBlank()) {
                assertThat(item.getSteps().get(1).result().name()).isEqualTo("SKIPPED");
            } else {
                assertThat(item.getSteps().get(1).result().name()).isIn("TAG_CREATED", "ALREADY_EXISTS");
                JsonNode tagEvidence = gitLabRequest(item.getRepo().value(), HttpMethod.GET,
                        "/repository/tags/" + encode(tag), null);
                assertThat(tagEvidence.path("commit").path("id").asText())
                        .isEqualTo(item.getMetadata().get("git.mergeSha"));
            }
        }
    }

    private void verifyMergedEvidence(String repoId, Map<String, String> metadata) {
        String projectId = gitLabProjectId(repoId);
        assertThat(metadata.get("git.projectId")).isEqualTo(projectId);
        assertThat(metadata.get("git.mrIid")).matches("[1-9][0-9]*");
        assertThat(metadata.get("git.sourceSha")).isNotBlank();
        assertThat(metadata.get("git.mergeSha")).isNotBlank();
        JsonNode mr = gitLabRequest(repoId, HttpMethod.GET, "/merge_requests/" + metadata.get("git.mrIid"), null);
        assertThat(mr.path("project_id").asText()).isEqualTo(projectId);
        assertThat(mr.path("source_project_id").asText()).isEqualTo(projectId);
        assertThat(mr.path("target_project_id").asText()).isEqualTo(projectId);
        assertThat(mr.path("source_branch").asText()).isEqualTo(metadata.get("git.sourceBranch"));
        assertThat(mr.path("target_branch").asText()).isEqualTo(metadata.get("git.targetBranch"));
        assertThat(mr.path("state").asText()).isEqualTo("merged");
        assertThat(mr.path("sha").asText()).isEqualTo(metadata.get("git.sourceSha"));
        assertThat(mr.path("merge_commit_sha").asText()).isEqualTo(metadata.get("git.mergeSha"));
        assertThat(mr.path("web_url").asText()).isEqualTo(metadata.get("git.mrUrl"));
        JsonNode containingRefs = gitLabRequest(repoId, HttpMethod.GET,
                "/repository/commits/" + encode(metadata.get("git.sourceSha")) + "/refs?type=branch&per_page=100", null);
        List<String> branches = new ArrayList<>();
        containingRefs.forEach(ref -> branches.add(ref.path("name").asText()));
        assertThat(branches).contains(metadata.get("git.sourceBranch"), metadata.get("git.targetBranch"));
    }

    private JsonNode pairMergeRequests(String repoId, Map<String, String> metadata) {
        return gitLabRequest(repoId, HttpMethod.GET, "/merge_requests?scope=all&state=all&per_page=100"
                + "&source_branch=" + encode(metadata.get("git.sourceBranch"))
                + "&target_branch=" + encode(metadata.get("git.targetBranch")), null);
    }

    private String scopeBody(List<String> repoIds, List<String> iterations) throws Exception {
        return objectMapper.writeValueAsString(Map.of("repoIds", repoIds, "iterationKeys", iterations, "failFast", true));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
                .andExpect(jsonPath("$.data.frozen").value(false))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

}
