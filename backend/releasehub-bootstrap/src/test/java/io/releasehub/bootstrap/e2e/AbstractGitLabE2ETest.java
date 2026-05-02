package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.Run;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 真实 GitLab E2E 测试基类 — 连接本机常驻 PostgreSQL + GitLab CE。
 *
 * <p>前置条件：</p>
 * <ul>
 *   <li>PostgreSQL 运行在 localhost:5433（docs/docker-compose.yml）</li>
 *   <li>GitLab CE 运行在 localhost:9080（docker-compose.gitlab.yml）</li>
 *   <li>已执行 scripts/e2e/init-gitlab.sh 创建种子数据</li>
 * </ul>
 *
 * <p>CRUD helpers 委托给 {@link E2ETestFixtures}，与 {@link AbstractE2ETest} 共享实现。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractGitLabE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected RunPort runPort;

    // ─── Delegated CRUD helpers ───

    protected String loginAndGetToken() throws Exception {
        return E2ETestFixtures.loginAndGetToken(mockMvc, objectMapper);
    }

    protected String createGroup(String token) throws Exception {
        return E2ETestFixtures.createGroup(mockMvc, token);
    }

    protected String createRepo(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createRepo(mockMvc, objectMapper, token, groupCode);
    }

    protected String createGitLabRepo(String token, String groupCode, String cloneUrl, String gitToken) throws Exception {
        return E2ETestFixtures.createGitLabRepo(mockMvc, objectMapper, token, groupCode, cloneUrl, gitToken);
    }

    protected String createIteration(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createIteration(mockMvc, objectMapper, token, groupCode);
    }

    protected String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
        return E2ETestFixtures.createIterationWithRepo(mockMvc, objectMapper, token, groupCode, repoId);
    }

    protected String createReleaseWindow(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createReleaseWindow(mockMvc, objectMapper, token, groupCode);
    }

    protected String getWindowKey(String token, String windowId) throws Exception {
        return E2ETestFixtures.getWindowKey(mockMvc, objectMapper, token, windowId);
    }

    // ─── Run verification ───

    protected String findRunIdByWindowName(String token, String windowName) throws Exception {
        var result = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", windowName)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data").isArray())
                .andReturn();
        var runs = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        if (runs.size() > 0) {
            return runs.get(0).get("id").asText();
        }
        return null;
    }

    protected void verifyRunItems(String runId, int expectedItemCount, List<String> expectedActionTypes) {
        if (runPort == null || runId == null) return;
        Run run = runPort.findById(runId).orElse(null);
        assertThat(run).withFailMessage("Run 记录应存在: %s", runId).isNotNull();
        assertThat(run.getItems()).withFailMessage("RunItem 数量应为 %d", expectedItemCount)
                .hasSize(expectedItemCount);
        if (!expectedActionTypes.isEmpty()) {
            run.getItems().forEach(item -> {
                List<String> stepTypes = item.getSteps().stream()
                        .<String>map(s -> s.actionType().name())
                        .toList();
                assertThat(stepTypes)
                        .withFailMessage("RunItem 的步骤应包含预期 ActionType 序列")
                        .containsExactlyElementsOf(expectedActionTypes);
            });
        }
    }

    // ─── GitLab API 辅助（用于制造冲突场景） ───

    private static final RestTemplate gitLabClient = new RestTemplate();

    protected boolean gitLabCreateBranch(String gitlabBaseUrl, String token, String projectId,
                                          String branchName, String fromBranch) {
        try {
            String url = String.format("%s/api/v4/projects/%s/repository/branches",
                    gitlabBaseUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8));
            HttpHeaders headers = new HttpHeaders();
            headers.set("PRIVATE-TOKEN", token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("branch", branchName, "ref", fromBranch);
            ResponseEntity<String> resp = gitLabClient.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean gitLabUpdateFile(String gitlabBaseUrl, String token, String projectId,
                                        String filePath, String newContent, String commitMessage) {
        try {
            String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            String url = String.format("%s/api/v4/projects/%s/repository/files/%s",
                    gitlabBaseUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8), encodedPath);
            HttpHeaders headers = new HttpHeaders();
            headers.set("PRIVATE-TOKEN", token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            String encodedContent = Base64.getEncoder().encodeToString(
                    newContent.getBytes(StandardCharsets.UTF_8));
            Map<String, String> body = Map.of(
                    "branch", "main", "content", encodedContent,
                    "commit_message", commitMessage, "encoding", "base64");
            ResponseEntity<String> resp = gitLabClient.exchange(
                    url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
