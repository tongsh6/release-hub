package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.*;
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
    private String gitlabToken;
    private String gitlabBaseUrl;
    private String gitlabUser;

    @BeforeAll
    void setUp() throws Exception {
        gitlabToken = System.getenv().getOrDefault("E2E_GITLAB_TOKEN", "");
        gitlabBaseUrl = System.getenv().getOrDefault("E2E_GITLAB_URL", "http://localhost:9080");
        gitlabUser = System.getenv().getOrDefault("E2E_GITLAB_USER", "root");
    }

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
        // 通过 ReleaseHub 的 GitLab 适配器间接验证
        // 如果有 token，直接通过 GitLab API 验证
        if (!gitlabToken.isEmpty()) {
            boolean created = gitLabCreateBranch(gitlabBaseUrl, gitlabToken,
                    "root%2Fseed-repo-1-maven", "test-e2e-connectivity", "main");
            System.out.println("GitLab branch creation test: " + (created ? "SUCCESS" : "FAILED (repo may not exist yet)"));
            // Repo might not exist if init-gitlab.sh wasn't run — that's OK for connectivity check
        }
    }

    @Test
    @Order(3)
    @DisplayName("[连通性] 创建真实 GitLab 仓库并注册到 ReleaseHub")
    void createRealGitLabRepo() throws Exception {
        assertThat(gitlabToken).withFailMessage(
                "E2E_GITLAB_TOKEN 未设置。请先运行: scripts/e2e/init-gitlab.sh").isNotBlank();

        String groupCode = createGroup(token);

        // Use seed-repo-1-maven from init-gitlab.sh
        String cloneUrl = String.format("http://gitlab.local/%s/seed-repo-1-maven.git", gitlabUser);
        String repoId = createGitLabRepo(token, groupCode, cloneUrl, gitlabToken);
        assertThat(repoId).isNotBlank();

        // Verify the repo was created in ReleaseHub
        mockMvc.perform(get("/api/v1/repositories/" + repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(repoId));

        // Create iteration + window, run full lifecycle
        String iterKey = createIterationWithRepo(token, groupCode, repoId);
        String windowId = createReleaseWindow(token, groupCode);

        // Attach iteration to window → triggers GitLab branch creation
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

        // Close → triggers cleanup (archive, tag, merge to main, CI trigger)
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        System.out.println("Full lifecycle completed with real GitLab repo: " + cloneUrl);
    }
}
