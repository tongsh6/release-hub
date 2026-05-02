package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 2 E2E: Repo + Iteration + BranchRule 全链路验证
 *
 * <p>覆盖 6 个用户故事共 9 个场景：
 * <ol>
 *   <li>Admin 创建 BranchRule (TEMPLATE)</li>
 *   <li>Admin 创建 BranchRule (REGEX)</li>
 *   <li>Admin 通过 Test API 验证分支名合规</li>
 *   <li>Developer 在叶子分组下创建仓库</li>
 *   <li>Tester 验证仓库列表查询</li>
 *   <li>Developer 创建迭代并关联仓库</li>
 *   <li>Tester 验证迭代关联的仓库列表</li>
 *   <li>Tester 验证迭代分页查询</li>
 *   <li>Developer 删除迭代</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Slice2_Repo_Iter_BranchRule_E2ETest extends AbstractE2ETest {

    private String token;
    private String groupCode;
    private String repoId;
    private String repoName;
    private String templateRuleId;
    private String regexRuleId;
    private String iterKey;

    // ═══════════════════════════════════════════════════════════════
    // Setup: Login as admin
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void setup() throws Exception {
        token = loginAndGetToken();
        groupCode = createGroup(token);
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 1: [Admin] 创建 BranchRule (TEMPLATE)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    void createBranchRuleTemplate() throws Exception {
        String name = "TC-Rule-Template-" + System.currentTimeMillis();

        var result = mockMvc.perform(post("/api/v1/branch-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"pattern\":\"feature/{iterationKey}\",\"type\":\"TEMPLATE\",\"description\":\"E2E template rule\",\"scopeLevel\":\"GLOBAL\",\"scopeProjectId\":null,\"scopeSubProjectId\":null}",
                                name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.pattern").value("feature/{iterationKey}"))
                .andExpect(jsonPath("$.data.type").value("TEMPLATE"))
                .andReturn();

        templateRuleId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 2: [Admin] 创建 BranchRule (REGEX)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    void createBranchRuleRegex() throws Exception {
        String name = "TC-Rule-Regex-" + System.currentTimeMillis();

        var result = mockMvc.perform(post("/api/v1/branch-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"pattern\":\"^feature/[A-Z]+-\\\\d+$\",\"type\":\"REGEX\",\"description\":\"E2E regex rule\",\"scopeLevel\":\"PROJECT\",\"scopeProjectId\":\"proj-e2e\",\"scopeSubProjectId\":null}",
                                name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.pattern").value("^feature/[A-Z]+-\\d+$"))
                .andExpect(jsonPath("$.data.type").value("REGEX"))
                .andReturn();

        regexRuleId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 3: [Admin] Test API 验证分支名合规
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    void checkBranchCompliant() throws Exception {
        // feature/ITER-001 should match both rules
        mockMvc.perform(get("/api/v1/branch-rules/check?branchName=feature/ITER-001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchName").value("feature/ITER-001"))
                .andExpect(jsonPath("$.data.compliant").value(true));

        // hotfix/urgent should not match either rule
        mockMvc.perform(get("/api/v1/branch-rules/check?branchName=hotfix/urgent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchName").value("hotfix/urgent"))
                .andExpect(jsonPath("$.data.compliant").value(false));
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 4: [Developer] 在叶子分组下创建仓库
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    void createRepositoryOnLeafGroup() throws Exception {
        repoName = "TC-Repo-" + System.currentTimeMillis();

        var result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}",
                                repoName, repoName, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        repoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 5: [Tester] 验证仓库列表查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    void listRepositories() throws Exception {
        // Plain list returns array
        mockMvc.perform(get("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNumber());

        // Paged list
        mockMvc.perform(get("/api/v1/repositories/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.page.page").value(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 6: [Developer] 创建迭代并关联仓库
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    void createIterationWithRepo() throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();

        var result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "description", "E2E Slice 2 iteration",
                                "groupCode", groupCode,
                                "repoIds", Set.of(repoId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn();

        iterKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("key").asText();

        // Verify key format: ITER-xxx
        org.assertj.core.api.Assertions.assertThat(iterKey)
                .startsWith("ITER-");
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 7: [Tester] 验证迭代关联的仓库列表
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    void listIterationRepos() throws Exception {
        mockMvc.perform(get("/api/v1/iterations/" + iterKey + "/repos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 8: [Tester] 验证迭代分页查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    void listIterationsPaged() throws Exception {
        mockMvc.perform(get("/api/v1/iterations/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.page.page").value(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // Scenario 9: [Developer] 删除迭代
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void deleteIteration() throws Exception {
        mockMvc.perform(delete("/api/v1/iterations/" + iterKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════
    // Cleanup: delete branch rules
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    void cleanupBranchRules() throws Exception {
        if (templateRuleId != null) {
            mockMvc.perform(delete("/api/v1/branch-rules/" + templateRuleId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        if (regexRuleId != null) {
            mockMvc.perform(delete("/api/v1/branch-rules/" + regexRuleId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }
}
