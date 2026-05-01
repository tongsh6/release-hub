package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整发布流程 E2E 测试：Group → Repo → Iteration → Window → Attach → Freeze → Publish → Close
 * 覆盖 B3 regression：null iterationKeys 不应导致 NPE
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReleaseFlowE2eTest extends AbstractE2ETest {

    private String token;
    private String groupCode;
    private String repoId;
    private String iterKey;
    private String windowId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    @Test
    @Order(1)
    void step1_createGroup() throws Exception {
        groupCode = "e2e-flow-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Flow Group\",\"code\":\"%s\",\"parentCode\":null}", groupCode)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void step2_createRepo() throws Exception {
        String name = "e2e-flow-repo-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"cloneUrl\":\"https://git.x.com/%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}", name, name, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andDo(result -> {
                    repoId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
                });
    }

    @Test
    @Order(3)
    void step3_createIteration() throws Exception {
        String name = "e2e-flow-iter-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "groupCode", groupCode, "repoIds", List.of(repoId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andDo(result -> {
                    iterKey = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
                });
    }

    @Test
    @Order(4)
    void step4_createWindow() throws Exception {
        String name = "e2e-flow-rw-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"groupCode\":\"%s\"}", name, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andDo(result -> {
                    windowId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
                });
    }

    @Test
    @Order(5)
    void step5_attachNullIterationKeysFails() throws Exception {
        // B3 regression: wrong field name results in null iterationKeys → validation error, not NPE
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongField\":[\"" + iterKey + "\"]}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(6)
    void step6_attachIteration() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void step7_listWindowIterations() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(8)
    void step8_freeze() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));
    }

    @Test
    @Order(9)
    void step9_publish() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @Order(10)
    void step10_close() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @Order(11)
    void step11_dashboardReflectsData() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRepositories").isNumber())
                .andExpect(jsonPath("$.data.totalIterations").isNumber());
    }

    @Test
    @Order(12)
    void step12_detachIteration() throws Exception {
        // create a fresh window + iteration to test detach
        String freshWinId = createReleaseWindow(token, groupCode);
        String freshIterKey = createIteration(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(freshIterKey)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/detach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKey", freshIterKey))))
                .andExpect(status().isOk());
    }
}
