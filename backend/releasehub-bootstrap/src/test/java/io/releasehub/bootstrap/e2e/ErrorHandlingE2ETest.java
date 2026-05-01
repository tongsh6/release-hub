package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 错误场景 E2E 测试（使用真实 PostgreSQL）
 * <p>
 * 覆盖：
 * - GROUP_014：向非叶子节点挂载仓库 / 发布窗口
 * - RW_009：对已 CLOSED 的发布窗口执行冻结操作
 * - @NotEmpty 验证：attach 时 iterationKeys 为空列表
 */
class ErrorHandlingE2ETest extends AbstractE2ETest {

    /**
     * GROUP_014：向非叶子节点（有子分组的分组）创建发布窗口时，
     * 应返回 400 + GROUP_014 错误码
     */
    @Test
    void should_reject_release_window_on_non_leaf_group() throws Exception {
        String token = loginAndGetToken();

        // 创建父分组
        String parentCode = "TC-PARENT-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Parent\",\"code\":\"%s\",\"parentCode\":null}", parentCode)))
                .andExpect(status().isOk());

        // 在父分组下创建子分组——父分组变为非叶子节点
        String childCode = "TC-CHILD-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Child\",\"code\":\"%s\",\"parentCode\":\"%s\"}", childCode, parentCode)))
                .andExpect(status().isOk());

        // 向非叶子节点（parentCode）创建发布窗口，期望 GROUP_014
        mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"TC-RW\",\"groupCode\":\"%s\"}", parentCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_014"));
    }

    /**
     * RW_009：对已 CLOSED 的发布窗口执行冻结操作，应返回 400 + RW_009
     * <p>
     * 状态机路径：DRAFT → freeze → DRAFT(frozen) → publish → PUBLISHED → close → CLOSED → freeze ✗
     */
    @Test
    void should_reject_freeze_on_closed_release_window() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);

        // 创建发布窗口并关联迭代（publish 前需有迭代）
        String windowId = createReleaseWindow(token, groupCode);
        String iterKey = createIteration(token, groupCode);
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iterationKeys\":[\"" + iterKey + "\"]}"))
                .andExpect(status().isOk());

        // 冻结 → 发布 → 关闭
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

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 对 CLOSED 窗口再次冻结，期望 RW_009（无效状态）
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RW_009"));
    }

    /**
     * attach 接口：iterationKeys 为空列表时，应返回 400（Bean Validation @NotEmpty）
     */
    @Test
    void should_reject_attach_with_empty_iteration_keys() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);
        String windowId = createReleaseWindow(token, groupCode);

        // iterationKeys 传空列表
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of()))))
                .andExpect(status().isBadRequest());
    }

    /**
     * attach 接口：迭代 key 不存在时，应返回 404（ITER_001 或 404 状态码）
     */
    @Test
    void should_reject_attach_with_nonexistent_iteration_key() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);
        String windowId = createReleaseWindow(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iterationKeys\":[\"ITER-NONEXISTENT-9999\"]}"))
                .andExpect(status().is4xxClientError());
    }
}
