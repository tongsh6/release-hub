package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

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
        return E2ETestFixtures.loginAndGetToken(mockMvc, objectMapper);
    }

    String createGroup(String token) throws Exception {
        return E2ETestFixtures.createGroup(mockMvc, token);
    }

    String createRepo(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createRepo(mockMvc, objectMapper, token, groupCode);
    }

    String createIteration(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createIteration(mockMvc, objectMapper, token, groupCode);
    }

    String createReleaseWindow(String token, String groupCode) throws Exception {
        return E2ETestFixtures.createReleaseWindow(mockMvc, objectMapper, token, groupCode);
    }
}
