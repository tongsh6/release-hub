package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Release 核心业务链路 E2E 测试（使用真实 PostgreSQL）
 * <p>
 * 验证完整流程：Group → Repo → Iteration → ReleaseWindow → Attach → Freeze → Publish
 */
class ReleaseWorkflowE2ETest extends AbstractE2ETest {

    @Test
    void should_complete_full_release_workflow() throws Exception {
        String token = loginAndGetToken();

        // Step 1: 创建叶子分组
        String groupCode = createGroup(token);

        // Step 2: 在分组下创建仓库
        String repoId = createRepo(token, groupCode);

        // Step 3: 创建迭代（关联仓库）
        String iterKey = createIterationWithRepo(token, groupCode, repoId);

        // Step 4: 创建发布窗口
        String windowId = createReleaseWindow(token, groupCode);

        // Step 5: 关联迭代到发布窗口
        attachIterationToWindow(token, windowId, iterKey);

        // Step 6: 冻结窗口
        MvcResult freezeResult = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterFreeze = objectMapper.readTree(freezeResult.getResponse().getContentAsString()).get("data");
        assertThat(afterFreeze.get("frozen").asBoolean()).isTrue();

        // Step 7: 发布窗口
        MvcResult publishResult = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterPublish = objectMapper.readTree(publishResult.getResponse().getContentAsString()).get("data");
        assertThat(afterPublish.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(afterPublish.get("frozen").asBoolean()).isTrue();
        assertThat(afterPublish.get("publishedAt").asText()).isNotBlank();
    }

    @Test
    void should_get_release_window_by_id() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);
        String windowId = createReleaseWindow(token, groupCode);

        MvcResult getResult = mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(windowId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        JsonNode data = objectMapper.readTree(getResult.getResponse().getContentAsString()).get("data");
        assertThat(data.get("id").asText()).isEqualTo(windowId);
    }

    @Test
    void should_list_iterations_after_attach() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);
        String windowId = createReleaseWindow(token, groupCode);

        String iterKey1 = createIteration(token, groupCode);
        String iterKey2 = createIteration(token, groupCode);

        // 关联两个迭代
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey1, iterKey2)))))
                .andExpect(status().isOk());

        // 查询已关联迭代列表
        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ──────────────────────────── 辅助方法 ────────────────────────────

    private String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();
        String req = String.format(
                "{\"name\":\"%s\",\"description\":\"E2E iter\",\"groupCode\":\"%s\",\"repoIds\":[\"%s\"]}",
                name, groupCode, repoId);
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

    private void attachIterationToWindow(String token, String windowId, String iterKey) throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());
    }

}
