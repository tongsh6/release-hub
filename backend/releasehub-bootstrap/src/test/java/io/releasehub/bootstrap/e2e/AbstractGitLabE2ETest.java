package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.Run;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 GitLab E2E 测试基类 — 连接本机常驻 PostgreSQL + GitLab CE。
 *
 * <p>前置条件：</p>
 * <ul>
 *   <li>PostgreSQL 运行在 localhost:5433（docs/docker-compose.yml）</li>
 *   <li>GitLab CE 运行在 localhost:9080（docker-compose.gitlab.yml）</li>
 *   <li>已执行 scripts/e2e/init-gitlab.sh 创建种子数据</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("gitlab-e2e-local")
public abstract class AbstractGitLabE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected RunPort runPort;

    String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    String createGroup(String token) throws Exception {
        String code = "TC-G-" + System.currentTimeMillis();
        String req = String.format("{\"name\":\"E2E-Group\",\"code\":\"%s\",\"parentCode\":null}", code);
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        return code;
    }

    String createRepo(String token, String groupCode) throws Exception {
        String name = "TC-Repo-" + System.currentTimeMillis();
        String req = String.format(
                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}",
                name, name, groupCode);
        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    /**
     * 创建连接真实 GitLab 的仓库（gitProvider=GITLAB, 真实 cloneUrl + token）
     */
    String createGitLabRepo(String token, String groupCode, String cloneUrl, String gitToken) throws Exception {
        String name = "TC-Repo-" + System.currentTimeMillis();
        String req = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "cloneUrl", cloneUrl,
                "groupCode", groupCode,
                "defaultBranch", "main",
                "gitProvider", "GITLAB",
                "gitToken", gitToken
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    String createIteration(String token, String groupCode) throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();
        String req = String.format("{\"name\":\"%s\",\"description\":\"E2E iter\",\"groupCode\":\"%s\"}", name, groupCode);
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("key").asText();
    }

    /**
     * 创建迭代并关联仓库（用于触发 feature 分支创建）
     */
    String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("key").asText();
    }

    String createReleaseWindow(String token, String groupCode) throws Exception {
        String name = "TC-RW-" + System.currentTimeMillis();
        String req = String.format("{\"name\":\"%s\",\"groupCode\":\"%s\"}", name, groupCode);
        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    /**
     * 通过 GET detail 获取窗口的 key 字段
     */
    String getWindowKey(String token, String windowId) throws Exception {
        var result = mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        if (data.has("key") && !data.get("key").isNull()) {
            return data.get("key").asText();
        }
        return windowId;
    }

    /**
     * 通过 window name 查询 Run ID，然后通过 RunPort 获取完整 Run 对象进行深验证。
     */
    protected String findRunIdByWindowName(String token, String windowName) throws Exception {
        var result = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", windowName)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        JsonNode runs = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        if (runs.size() > 0) {
            return runs.get(0).get("id").asText();
        }
        return null;
    }

    /**
     * 验证 Run 包含指定数量的 RunItem，每个 RunItem 含预期 ActionType 序列。
     */
    // ─── GitLab API 辅助（用于制造冲突场景） ───

    private static final RestTemplate gitLabClient = new RestTemplate();

    /**
     * 在 GitLab 仓库中创建分支
     */
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

    /**
     * 修改 GitLab 仓库中的文件内容（用于制造版本冲突）
     */
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
}
