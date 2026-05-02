package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

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

    /**
     * 创建连接真实 GitLab 的仓库（gitProvider=GITLAB, 真实 cloneUrl + token）。
     */
    public static String createGitLabRepo(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                                           String groupCode, String cloneUrl, String gitToken) throws Exception {
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

    public static String getWindowKey(MockMvc mockMvc, ObjectMapper objectMapper,
                                       String token, String windowId) throws Exception {
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
}
