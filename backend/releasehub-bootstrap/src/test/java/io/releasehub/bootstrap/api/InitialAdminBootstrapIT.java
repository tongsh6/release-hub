package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.auth.InitialAdminAppService;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.infrastructure.seed.InitialAdminInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用隔离空库和生产配置验证首次登录，不依赖开发 seed 或 GitLab。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prd", "real"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InitialAdminBootstrapIT {
    private static final String PASSWORD = "Initial-admin-test-2026!";
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("initial_admin_test")
            .withUsername("bootstrap_test")
            .withPassword("isolated-test-database");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.secret", () -> "isolated-bootstrap-jwt-key-at-least-32-bytes");
        registry.add("releasehub.bootstrap-admin.username", () -> "release-owner");
        registry.add("releasehub.bootstrap-admin.password", () -> PASSWORD);
        registry.add("releasehub.crypto.secret-key", () -> "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialAdminAppService service;

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    @Order(1)
    void freshProductionDatabaseSupportsLoginWithoutDevelopmentSeed() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "release-owner", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("release-owner"));
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from users", String.class))
                .startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    @Order(2)
    void anotherProductionStartupWithoutCredentialsPreservesExistingAccount() throws Exception {
        String hash = jdbc.queryForObject("select password_hash from users", String.class);
        try (var restarted = startProduction("different-owner", "")) {
            var restartedMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) restarted)
                    .apply(springSecurity()).build();
            restartedMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", "release-owner", "password", PASSWORD))))
                    .andExpect(status().isOk());
            // 即使后来提供另一个用户名和密码，也不应追加或重置已有账户。
            assertThat(service.initialize("another-owner", "Different-test-password!")).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select password_hash from users", String.class)).isEqualTo(hash);
        }
    }

    @Test
    @Order(3)
    void concurrentInitializationCreatesOnlyOneAccount() throws Exception {
        jdbc.update("delete from users"); // 仅清空本测试类独占的临时数据库。
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.initialize("owner-one", PASSWORD);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.initialize("owner-two", PASSWORD);
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(15, TimeUnit.SECONDS) ^ second.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    @Order(4)
    void emptyDatabaseWithoutCredentialsFailsStartupWithoutCreatingUser() {
        jdbc.update("delete from users");
        assertThatThrownBy(() -> {
            try (var ignored = startProduction("admin", "")) {
                throw new AssertionError("空用户表缺少密码时不应启动成功");
            }
        }).hasStackTraceContaining("RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }

    @Test
    @Order(5)
    void rejectsDevelopmentSeedAlongsideProductionInitialization() {
        var initializer = new InitialAdminInitializer(service, "admin", PASSWORD, true);
        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("seed");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }

    private ConfigurableApplicationContext startProduction(String username, String password) {
        return new SpringApplicationBuilder(ReleaseHubApplication.class)
                .initializers(context -> TestPropertyValues.of(Map.of(
                        "spring.datasource.url", POSTGRES.getJdbcUrl(),
                        "spring.datasource.username", POSTGRES.getUsername(),
                        "spring.datasource.password", POSTGRES.getPassword(),
                        "security.jwt.secret", "isolated-bootstrap-jwt-key-at-least-32-bytes",
                        "releasehub.bootstrap-admin.username", username,
                        "releasehub.crypto.secret-key", "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=",
                        "releasehub.bootstrap-admin.password", password
                )).applyTo(context.getEnvironment()))
                .run("--spring.profiles.active=prd,real", "--server.port=0");
    }
}
