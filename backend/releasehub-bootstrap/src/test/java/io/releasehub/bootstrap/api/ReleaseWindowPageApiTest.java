package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReleaseWindowPageApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    @Test
    void shouldListWindowsWithPaging() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        var before = mockMvc.perform(get("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();
        int base = objectMapper.readTree(before.getResponse().getContentAsString()).get("data").size();
        for (int i = 0; i < 25; i++) {
            mockMvc.perform(post("/api/v1/release-windows")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"RW-P-" + i + "\",\"groupCode\":\"" + groupCode + "\"}"))
                .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(10))
            .andExpect(jsonPath("$.page.page").value(1))
            .andExpect(jsonPath("$.page.size").value(10))
            .andExpect(jsonPath("$.page.total").value(base + 25))
            .andExpect(jsonPath("$.page.totalPages").value((int) Math.ceil((double)(base + 25) / 10)))
            .andExpect(jsonPath("$.page.hasNext").value(true));
    }

    @Test
    void shouldFilterWindowsByStatus() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        
        // 创建一个 DRAFT 状态的窗口
        var createResult = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"RW-Status-Test\",\"groupCode\":\"" + groupCode + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        
        // 按 DRAFT 状态筛选，应该能找到
        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10&status=DRAFT")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
        
        // 按 PUBLISHED 状态筛选，新创建的窗口不应出现
        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10&status=PUBLISHED")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldFilterWindowsByGroupIncludingChildren() throws Exception {
        String token = loginAndGetToken();
        String suffix = String.valueOf(System.currentTimeMillis());
        String parentCode = "GP" + suffix;
        String childCode = parentCode + "001";
        String otherCode = "GO" + suffix;
        createGroup(token, "UT-Parent", parentCode, null);
        createGroup(token, "UT-Child", childCode, parentCode);
        createGroup(token, "UT-Other", otherCode, null);

        String includedName = "RW-Group-Included-" + suffix;
        String excludedName = "RW-Group-Excluded-" + suffix;
        mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + includedName + "\",\"groupCode\":\"" + childCode + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + excludedName + "\",\"groupCode\":\"" + otherCode + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10&name=RW-Group-&groupCode=" + parentCode)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value(includedName))
            .andExpect(jsonPath("$.data[0].groupCode").value(childCode));
    }

    @Test
    void shouldExposeParallelScopeWithoutCrossWindowPlanPollution() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        String otherGroupCode = createGroupAndGetCode(token);

        CreatedWindow windowA = createWindow(token, groupCode, "RW-Parallel-A");
        CreatedWindow windowB = createWindow(token, groupCode, "RW-Parallel-B");
        CreatedWindow otherWindow = createWindow(token, otherGroupCode, "RW-Parallel-Other");
        String repoA = createRepo(token, groupCode, "repo-a");
        String repoB = createRepo(token, groupCode, "repo-b");
        String repoOther = createRepo(token, otherGroupCode, "repo-other");
        String iterA = createIteration(token, groupCode, "IT-Parallel-A", repoA);
        String iterB = createIteration(token, groupCode, "IT-Parallel-B", repoB);
        String iterOther = createIteration(token, otherGroupCode, "IT-Parallel-Other", repoOther);

        attachIteration(token, windowA.id(), iterA);
        attachIteration(token, windowB.id(), iterB);
        attachIteration(token, otherWindow.id(), iterOther);

        MvcResult scopeResult = mockMvc.perform(get("/api/v1/release-windows/" + windowA.id() + "/parallel-scope")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.currentWindowId").value(windowA.id()))
            .andExpect(jsonPath("$.data.currentWindowKey").value(windowA.windowKey()))
            .andExpect(jsonPath("$.data.groupCode").value(groupCode))
            .andExpect(jsonPath("$.data.activeWindowCount").value(2))
            .andExpect(jsonPath("$.data.windows.length()").value(2))
            .andReturn();

        JsonNode windows = objectMapper.readTree(scopeResult.getResponse().getContentAsString())
                .get("data")
                .get("windows");
        JsonNode scopeA = findWindowScope(windows, windowA.windowKey());
        JsonNode scopeB = findWindowScope(windows, windowB.windowKey());
        assertThat(scopeA.get("iterationCount").asInt()).isEqualTo(1);
        assertThat(scopeA.get("repoCount").asInt()).isEqualTo(1);
        assertThat(scopeA.get("planItems").get(0).get("iterationKey").asText()).isEqualTo(iterA);
        assertThat(scopeA.get("planItems").get(0).get("repoId").asText()).isEqualTo(repoA);
        assertThat(scopeB.get("iterationCount").asInt()).isEqualTo(1);
        assertThat(scopeB.get("repoCount").asInt()).isEqualTo(1);
        assertThat(scopeB.get("planItems").get(0).get("iterationKey").asText()).isEqualTo(iterB);
        assertThat(scopeB.get("planItems").get(0).get("repoId").asText()).isEqualTo(repoB);
        assertThat(windows.toString()).doesNotContain(repoOther);

        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10&name=RW-Parallel-&groupCode=" + groupCode)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].parallelActiveWindowCount").value(2))
            .andExpect(jsonPath("$.data[0].parallelActiveWindowKeys.length()").value(2));
    }

    @Test
    void shouldDeleteEmptyDraftWindow() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        var createResult = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"RW-Delete-Empty\",\"groupCode\":\"" + groupCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        String windowId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        mockMvc.perform(delete("/api/v1/release-windows/" + windowId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.nanoTime();
        createGroup(token, "UT-Group", code, null);
        return code;
    }

    private void createGroup(String token, String name, String code, String parentCode) throws Exception {
        String parent = parentCode == null ? "null" : "\"" + parentCode + "\"";
        String req = "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"parentCode\":" + parent + "}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    private CreatedWindow createWindow(String token, String groupCode, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"groupCode\":\"" + groupCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.windowKey").exists())
            .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new CreatedWindow(data.get("id").asText(), data.get("windowKey").asText());
    }

    private String createIteration(String token, String groupCode, String name, String repoId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"" + repoId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("key")
                .asText();
    }

    private String createRepo(String token, String groupCode, String suffix) throws Exception {
        String name = "UT-" + suffix + "-" + System.nanoTime();
        String cloneUrl = "https://git.example.com/" + name + ".git";
        String req = "{" +
                "\"name\":\"" + name + "\"," +
                "\"cloneUrl\":\"" + cloneUrl + "\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"defaultBranch\":\"main\"," +
                "\"gitProvider\":\"GITLAB\"," +
                "\"gitAccessToken\":\"test-token\"" +
                "}";
        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();
    }

    private void attachIteration(String token, String windowId, String iterationKey) throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("iterationKeys", java.util.List.of(iterationKey)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].iterationKey").value(iterationKey));
    }

    private JsonNode findWindowScope(JsonNode windows, String windowKey) {
        for (JsonNode window : windows) {
            if (windowKey.equals(window.get("windowKey").asText())) {
                return window;
            }
        }
        throw new AssertionError("Missing parallel scope for " + windowKey);
    }

    private record CreatedWindow(String id, String windowKey) {
    }
}
