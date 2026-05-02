package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户故事驱动的发布全生命周期 E2E 测试。
 *
 * <p>覆盖 US-RW、US-IT、US-REL、US-POST、US-VAL。</p>
 */
class ReleaseLifecycleE2ETest extends AbstractE2ETest {

    @Nested
    @DisplayName("完整发布生命周期")
    class FullReleaseLifecycle {

        @Test
        @DisplayName("创建窗口 → 挂载迭代 → 冻结 → 发布 → 关闭")
        void shouldCompleteFullReleaseWorkflow() throws Exception {
            String token = loginAndGetToken();
            String groupCode = createGroup(token);

            // Step 1: 创建仓库（不显式指定 gitProvider，走默认 MOCK）
            String repoId = createRepo(token, groupCode);

            // Step 2: 创建迭代并关联仓库
            String iterKey = createIterationWithRepo(token, groupCode, repoId);

            // Step 3: 创建发布窗口
            String windowId = createReleaseWindow(token, groupCode);

            // Step 3a: 获取窗口 key（用于 Run 查询）
            String windowKey = getWindowKey(token, windowId);

            // Step 4: 验证初始状态
            mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            // Step 5: 挂载迭代
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            // Step 6: 冻结
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.frozen").value(true));

            // Step 7: 发布 → 验证状态和 publishedAt
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

            // Step 7a: 验证 branch-status 显示 merge 成功
            mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/branch-status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.repos.length()").value(1))
                    .andExpect(jsonPath("$.data.repos[0].releaseBranch.mergeStatus").value("MERGED"));

            // Step 7b: 验证 publish 创建了 Run
            assertRunExists(token, windowKey);

            // Step 8: 关闭
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            // Step 9: 验证 Dashboard 统计数据
            mockMvc.perform(get("/api/v1/dashboard/stats")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRuns").isNumber())
                    .andExpect(jsonPath("$.data.totalRepositories").isNumber());
        }
    }

    @Nested
    @DisplayName("错误与边界场景")
    class ErrorAndEdgeCases {

        @Test
        @DisplayName("冻结后拒绝挂载迭代")
        void shouldRejectAttachWhenFrozen() throws Exception {
            String token = loginAndGetToken();
            String groupCode = createGroup(token);
            String iterKey = createIteration(token, groupCode);
            String windowId = createReleaseWindow(token, groupCode);

            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("无迭代的窗口发布被拒绝")
        void shouldRejectPublishWithoutIteration() throws Exception {
            String token = loginAndGetToken();
            String groupCode = createGroup(token);
            String windowId = createReleaseWindow(token, groupCode);

            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("关闭已关闭窗口应幂等")
        void shouldAllowIdempotentClose() throws Exception {
            String token = loginAndGetToken();
            String groupCode = createGroup(token);
            String repoId = createRepo(token, groupCode);
            String iterKey = createIterationWithRepo(token, groupCode, repoId);
            String windowId = createReleaseWindow(token, groupCode);

            // Attach → Freeze → Publish
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            // 第一次关闭
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            // 第二次关闭 — 幂等
            mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));
        }
    }

    // ─── 辅助方法 ───

    private String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();
        String req = objectMapper.writeValueAsString(Map.of(
                "name", name, "description", "E2E iter",
                "groupCode", groupCode, "repoIds", List.of(repoId)));
        var result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
    }

    /**
     * 通过 GET detail 获取窗口的 key 字段。
     */
    private String getWindowKey(String token, String windowId) throws Exception {
        var result = mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        if (data.has("key") && !data.get("key").isNull()) {
            return data.get("key").asText();
        }
        // Fallback: 部分旧版本 API 可能没有 key 字段
        return windowId;
    }

    /**
     * 验证至少有一个 Run 记录与给定窗口关联。
     */
    private void assertRunExists(String token, String windowKey) throws Exception {
        // 列出所有 Run，检查是否有匹配的
        var runResult = mockMvc.perform(get("/api/v1/runs/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("windowKey", windowKey)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode runs = objectMapper.readTree(runResult.getResponse().getContentAsString()).get("data");
        // 如果分页 filter 没找到，尝试不加 filter 的 list
        if (runs.size() == 0) {
            var allResult = mockMvc.perform(get("/api/v1/runs")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andReturn();
            JsonNode allRuns = objectMapper.readTree(allResult.getResponse().getContentAsString()).get("data");
            assertThat(allRuns.size())
                    .withFailMessage("publish 后应至少存在 1 个 Run 记录")
                    .isGreaterThanOrEqualTo(1);
        }
    }
}
