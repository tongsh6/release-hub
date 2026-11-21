package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared E2E test fixture helpers — used by both {@link AbstractE2ETest} (TestContainers + e2e profile)
 * and {@link AbstractGitLabE2ETest} (local PostgreSQL + real GitLab).
 */
public final class E2ETestFixtures {

    private E2ETestFixtures() {}

    /** 此值只保存本次 E2E 新建远端的身份，不携带凭据。 */
    record GitLabRepositoryFixture(String projectId, String cloneUrl) {}

    /**
     * 真实 E2E 的外部 fixture：每个本地仓库独占一个新 GitLab 项目。
     * 配置必须来自 init-gitlab.sh 导出的专用测试环境，禁止退回示例 URL 或匿名访问。
     */
    static GitLabRepositoryFixture createIsolatedGitLabProject(
            RestTemplate client, Map<String, String> environment) {
        String endpoint = requiredEnvironment(environment, "E2E_GITLAB_URL");
        String accessToken = requiredEnvironment(environment, "E2E_GITLAB_TOKEN");
        String username = requiredEnvironment(environment, "E2E_GITLAB_USER");
        URI base;
        try {
            base = URI.create(endpoint);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("E2E_GITLAB_URL 必须是无凭据的 HTTP(S) 地址");
        }
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getRawUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalStateException("E2E_GITLAB_URL 必须是无凭据的 HTTP(S) 地址");
        }
        if (!username.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalStateException("E2E_GITLAB_USER 必须是专用测试账号的用户名");
        }
        String baseUrl = endpoint.replaceAll("/+$", "");
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode account = client.exchange(URI.create(baseUrl + "/api/v4/user"), HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getBody();
        if (account == null || !username.equals(account.path("username").asText())) {
            throw new IllegalStateException("GitLab 当前账号与 E2E_GITLAB_USER 不一致，未创建测试项目");
        }

        String path = "releasehub-e2e-" + UUID.randomUUID();
        Map<String, Object> request = Map.of(
                "name", path, "path", path, "visibility", "private",
                "initialize_with_readme", true, "default_branch", "main", "auto_devops_enabled", false);
        JsonNode project = client.exchange(URI.create(baseUrl + "/api/v4/projects"), HttpMethod.POST,
                new HttpEntity<>(request, headers), JsonNode.class).getBody();
        String fullPath = username + "/" + path;
        if (project == null || !project.path("id").isIntegralNumber() || project.path("id").asLong() <= 0
                || !path.equals(project.path("path").asText())
                || !fullPath.equals(project.path("path_with_namespace").asText())
                || !"main".equals(project.path("default_branch").asText())) {
            throw new IllegalStateException("GitLab 新建项目的身份或默认分支未得到确认");
        }
        String projectId = project.path("id").asText();
        JsonNode main = client.exchange(
                URI.create(baseUrl + "/api/v4/projects/" + projectId + "/repository/branches/main"),
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        if (main == null || !"main".equals(main.path("name").asText())
                || main.path("commit").path("id").asText().isBlank()) {
            throw new IllegalStateException("GitLab 新建项目尚无已确认的 main 提交");
        }
        // external_url 可能是容器外别名；沿用 seed 脚本的 E2E 可达地址及已核实项目路径。
        return new GitLabRepositoryFixture(projectId, baseUrl + "/" + fullPath + ".git");
    }

    private static String requiredEnvironment(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 未设置，请先运行 scripts/e2e/init-gitlab.sh 并加载其环境文件");
        }
        return value;
    }

    public static String loginAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
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

    public static String createGroup(MockMvc mockMvc, String token) throws Exception {
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

    public static String createRepo(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                                     String groupCode) throws Exception {
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

    /** 仅在专用 E2E 账号与隔离项目已核验后调用，校准安全门读取的持久设置。 */
    static void configureGitLabSettings(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                                        String baseUrl, String gitAccessToken) throws Exception {
        mockMvc.perform(post("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseUrl", baseUrl, "token", gitAccessToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(get("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseUrl").value(baseUrl));
    }

    /**
     * 创建连接真实 GitLab 的仓库（gitProvider=GITLAB, 真实 cloneUrl + token）。
     */
    public static String createGitLabRepo(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                                           String groupCode, String cloneUrl, String gitAccessToken) throws Exception {
        String name = "TC-Repo-" + System.currentTimeMillis();
        String req = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "cloneUrl", cloneUrl,
                "groupCode", groupCode,
                "defaultBranch", "main",
                "gitProvider", "GITLAB",
                "gitAccessToken", gitAccessToken
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

    public static String createIteration(MockMvc mockMvc, ObjectMapper objectMapper,
                                          String token, String groupCode) throws Exception {
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

    public static String createIterationWithRepo(MockMvc mockMvc, ObjectMapper objectMapper,
                                                   String token, String groupCode, String repoId) throws Exception {
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

    public static String createReleaseWindow(MockMvc mockMvc, ObjectMapper objectMapper,
                                              String token, String groupCode) throws Exception {
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
     * 验证关闭已受理及真实 Run 身份；调用方必须另行断言 Git 成功，HTTP 200 不代表收尾 DONE。
     */
    public static JsonNode closeAndReadRun(MockMvc mockMvc, ObjectMapper objectMapper,
                                           String token, String windowId, boolean triggerCi) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/release-windows/{id}/close", windowId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("triggerCi", triggerCi))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runType").value("WINDOW_CLEANUP"))
                .andExpect(jsonPath("$.data.id").isNotEmpty()).andReturn();
        JsonNode run = objectMapper.readTree(response.getResponse().getContentAsString()).path("data");
        org.assertj.core.api.Assertions.assertThat(run.path("status").asText())
                .isIn("RUNNING", "DONE", "PARTIAL_FAILED", "FAILED", "NEEDS_REVIEW");
        org.assertj.core.api.Assertions.assertThat(run.path("items")).isNotEmpty();
        for (JsonNode item : run.path("items")) {
            org.assertj.core.api.Assertions.assertThat(item.path("iterationKey").isNull()).isTrue();
            org.assertj.core.api.Assertions.assertThat(item.path("metadata").path("plan.triggerCi").asText())
                    .isEqualTo(Boolean.toString(triggerCi));
        }
        mockMvc.perform(get("/api/v1/release-windows/{id}", windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        return run;
    }

    public static String getWindowKey(MockMvc mockMvc, ObjectMapper objectMapper,
                                       String token, String windowId) throws Exception {
        var result = mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String key = data.path("windowKey").asText();
        org.assertj.core.api.Assertions.assertThat(key).isNotBlank();
        return key;
    }
}
