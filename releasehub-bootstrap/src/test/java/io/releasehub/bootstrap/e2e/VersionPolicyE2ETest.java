package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本策略 E2E 测试（使用真实 PostgreSQL）
 * <p>
 * 目的：验证 PostgreSQL 特有语法（cast(:keyword as varchar)）在分页 + keyword 搜索中正常工作。
 * H2 与 PostgreSQL 在类型转换 SQL 上存在差异，此测试必须用真实 PG 才能覆盖该差异。
 */
class VersionPolicyE2ETest extends AbstractE2ETest {

    @Test
    void should_list_version_policies_with_pagination() throws Exception {
        String token = loginAndGetToken();

        // 第 1 页、每页 10 条——验证分页响应结构正常
        mockMvc.perform(get("/api/v1/version-policies/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(10));
    }

    @Test
    void should_search_version_policies_by_keyword() throws Exception {
        String token = loginAndGetToken();

        // keyword 搜索——这条 SQL 在 PostgreSQL 中使用 cast(:keyword as varchar)
        // 如果 cast 语法不兼容，会抛 SQL 异常导致 500；此处期望正常返回 200
        mockMvc.perform(get("/api/v1/version-policies/paged?page=1&size=10&keyword=SEMVER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void should_search_version_policies_with_empty_keyword() throws Exception {
        String token = loginAndGetToken();

        // keyword 为空字符串时应等同于不过滤
        mockMvc.perform(get("/api/v1/version-policies/paged?page=1&size=20&keyword=")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void should_list_all_version_policies() throws Exception {
        String token = loginAndGetToken();

        // 非分页接口——返回全量列表
        mockMvc.perform(get("/api/v1/version-policies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
