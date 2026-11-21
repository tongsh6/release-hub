package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GitLab 连通性验证 — 证明真实 GitLab（localhost:9080）与 ReleaseHub 全链路打通。
 *
 * <p>前置条件：docker compose -f docker-compose.gitlab.yml up -d</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GitLabConnectivityE2ETest extends AbstractGitLabE2ETest {

    private String token;
    private String groupCode;
    private String repoId;

    @Test
    @Order(1)
    @DisplayName("[连通性] 登录 ReleaseHub")
    void login() throws Exception {
        token = loginAndGetToken();
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("[连通性] 验证 GitLab 可访问")
    void verifyGitLabAccessible() throws Exception {
        groupCode = createGroup(token);
        repoId = createRepo(token, groupCode);
        var main = gitLabRequest(repoId, HttpMethod.GET, "/repository/branches/main", null);
        assertThat(gitLabProjectId(repoId)).matches("[1-9][0-9]*");
        assertThat(main.path("name").asText()).isEqualTo("main");
        assertThat(main.path("commit").path("id").asText()).matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");
    }

    @Test
    @Order(3)
    @DisplayName("[连通性] 验证隔离 GitLab 仓库的注册与发布生命周期")
    void createRealGitLabRepo() throws Exception {
        assertThat(repoId).as("仓库必须由本次隔离 fixture 创建并完成 Settings 核验").isNotBlank();

        // Verify the repo was created in ReleaseHub
        mockMvc.perform(get("/api/v1/repositories/" + repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(repoId));

        // Create iteration + window, run full lifecycle
        String iterKey = createIterationWithRepo(token, groupCode, repoId);
        String windowId = createReleaseWindow(token, groupCode);

        // Attach only saves configuration; explicit Orchestrate below performs GitLab writes.
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());

        // Freeze → Publish
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/unfreeze")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var orchestration = mockMvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Idempotency-Key", "connectivity-orchestrate-" + windowId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("repoIds", List.of(repoId),
                                "iterationKeys", List.of(iterKey), "failFast", true))))
                .andExpect(status().isAccepted()).andReturn();
        String runId = objectMapper.readTree(orchestration.getResponse().getContentAsString()).path("data").asText();
        E2ETestFixtures.awaitWindowRun(runPort, runId);
        mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"));

        // Close merges release to default, tags only from the saved plan, retains refs, and leaves CI off.
        var cleanup = E2ETestFixtures.closeAndReadRun(mockMvc, objectMapper, token, windowId, false);
        assertThat(cleanup.path("status").asText()).isEqualTo("DONE");
        assertThat(cleanup.path("items").get(0).path("metadata").path("cleanup.referencePolicy").asText()).isEqualTo("RETAIN");

        System.out.println("Full lifecycle completed with isolated GitLab project: " + gitLabProjectId(repoId));
    }
}
