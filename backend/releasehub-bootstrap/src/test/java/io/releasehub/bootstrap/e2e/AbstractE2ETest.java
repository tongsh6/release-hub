package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E 测试基类：使用 TestContainers 启动真实 PostgreSQL 实例
 * <p>
 * 采用 Singleton 容器模式：容器在 JVM 启动时初始化一次，生命周期持续到 JVM 退出，
 * 所有继承此类的测试类共享同一个容器实例，避免多个测试类之间反复停启容器。
 * 测试数据使用时间戳前缀（"TC-" + millis），无需手动清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
abstract class AbstractE2ETest {

    // Singleton 容器模式：静态初始化，只启动一次，由 JVM shutdown hook 负责清理
    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("releasehub_e2e")
                .withUsername("e2e")
                .withPassword("e2e");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ──────────────────────────── Auth ────────────────────────────

    /**
     * 用 admin/admin 登录并返回 JWT token
     */
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

    // ──────────────────────────── Group ────────────────────────────

    /**
     * 创建叶子分组，返回 code
     *
     * @param token 认证 token
     * @return 新建分组的 code
     */
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

    // ──────────────────────────── Repo ────────────────────────────

    /**
     * 在指定分组下创建仓库，返回 repo id
     *
     * @param token     认证 token
     * @param groupCode 所属分组 code
     * @return 新建仓库的 id
     */
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

    // ──────────────────────────── Iteration ────────────────────────────

    /**
     * 在指定分组下创建迭代，返回 iteration key
     *
     * @param token     认证 token
     * @param groupCode 所属分组 code
     * @return 新建迭代的 key（格式：ITER-xxx）
     */
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

    // ──────────────────────────── Release Window ────────────────────────────

    /**
     * 在指定分组下创建发布窗口，返回 id
     *
     * @param token     认证 token
     * @param groupCode 所属分组 code
     * @return 新建发布窗口的 id
     */
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
}
