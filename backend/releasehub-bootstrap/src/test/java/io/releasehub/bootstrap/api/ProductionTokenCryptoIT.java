package io.releasehub.bootstrap.api;

import com.sun.net.httpserver.HttpServer;
import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.infrastructure.git.GitLabGitBranchAdapter;
import io.releasehub.infrastructure.persistence.repo.CodeRepositoryJpaEntity;
import io.releasehub.infrastructure.persistence.repo.CodeRepositoryJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真实 PostgreSQL 和本机 HTTP fixture 验证密文备份恢复，不访问真实 GitLab。 */
class ProductionTokenCryptoIT {
    private static final String KEY = Base64.getEncoder().encodeToString(
            "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));
    private static final String SETTINGS_TOKEN = "glpat-settings-crypto-fixture";
    private static final String REPO_TOKEN = "glpat-repository-crypto-fixture";
    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static final AtomicInteger ACCEPTED_REQUESTS = new AtomicInteger();
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("crypto_base").withUsername("crypto_test").withPassword("isolated-crypto-db");
    private static HttpServer gitlab;

    @BeforeAll
    static void startFixtures() throws Exception {
        POSTGRES.start();
        gitlab = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gitlab.createContext("/api/v4/", exchange -> {
            boolean userRequest = exchange.getRequestURI().getPath().equals("/api/v4/user");
            String expected = userRequest ? SETTINGS_TOKEN : REPO_TOKEN;
            boolean accepted = expected.equals(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            if (accepted) ACCEPTED_REQUESTS.incrementAndGet();
            byte[] body = (userRequest ? "{\"id\":1}" : "{\"commit\":{\"id\":\"fixture-commit\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(accepted ? 200 : 401, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        gitlab.start();
    }

    @AfterAll
    static void stopFixtures() {
        if (gitlab != null) gitlab.stop(0);
        POSTGRES.stop();
    }

    @Test
    void encryptsTokensAcrossRestartAndDatabaseRestore() throws Exception {
        String database = newDatabase();
        String settingsCiphertext;
        String repoCiphertext;
        int before = ACCEPTED_REQUESTS.get();
        try (var context = start(database, KEY)) {
            saveTokens(context);
            settingsCiphertext = jdbc(database).queryForObject("select gitlab_token from system_settings", String.class);
            repoCiphertext = jdbc(database).queryForObject("select git_token from code_repository", String.class);
            assertThat(settingsCiphertext).startsWith("enc:v1:").doesNotContain(SETTINGS_TOKEN);
            assertThat(repoCiphertext).startsWith("enc:v1:").doesNotContain(REPO_TOKEN);
            verifyConnections(context);
        }
        try (var restarted = start(database, KEY)) {
            verifyConnections(restarted);
            assertThat(jdbc(database).queryForObject("select gitlab_token from system_settings", String.class))
                    .isEqualTo(settingsCiphertext);
        }

        String restored = newDatabase();
        String backup = "/tmp/" + database + ".sql";
        command("pg_dump", "-U", POSTGRES.getUsername(), "-d", database, "-f", backup);
        String dump = POSTGRES.execInContainer("cat", backup).getStdout();
        assertThat(dump).contains(settingsCiphertext, repoCiphertext).doesNotContain(SETTINGS_TOKEN, REPO_TOKEN, KEY);
        command("psql", "-U", POSTGRES.getUsername(), "-v", "ON_ERROR_STOP=1", "-d", restored, "-f", backup);
        try (var context = start(restored, KEY)) {
            verifyConnections(context);
            assertThat(jdbc(restored).queryForObject("select gitlab_token from system_settings", String.class))
                    .isEqualTo(settingsCiphertext);
            assertThat(jdbc(restored).queryForObject("select git_token from code_repository", String.class))
                    .isEqualTo(repoCiphertext);
        }
        assertThat(ACCEPTED_REQUESTS.get() - before).isEqualTo(6);
    }

    @Test
    void rejectsMissingMalformedAndWrongLengthKeys() throws Exception {
        String database = newDatabase();
        for (String key : new String[]{"", "not-base64!", Base64.getEncoder().encodeToString(new byte[16])}) {
            assertThatThrownBy(() -> {
                try (var ignored = start(database, key)) {
                    throw new AssertionError("无效密钥不应启动成功");
                }
            }).hasStackTraceContaining("RELEASEHUB_CRYPTO_SECRET_KEY");
        }
    }

    @Test
    void rejectsWrongKeyAndDamagedCiphertextWithoutModifyingStoredTokens() throws Exception {
        String database = newDatabase();
        try (var context = start(database, KEY)) {
            saveTokens(context);
        }
        String ciphertext = jdbc(database).queryForObject("select gitlab_token from system_settings", String.class);
        String wrongKey = Base64.getEncoder().encodeToString(new byte[32]);
        assertThatThrownBy(() -> {
            try (var ignored = start(database, wrongKey)) {
                throw new AssertionError("错误密钥不应启动成功");
            }
        }).hasStackTraceContaining("Git Token 校验失败");
        assertThat(jdbc(database).queryForObject("select gitlab_token from system_settings", String.class))
                .isEqualTo(ciphertext);

        // 隔离 fixture 人为损坏仓库密文，验证另一存储位置也受启动门禁保护。
        jdbc(database).update("update code_repository set git_token = ?", "enc:v1:damaged");
        assertThatThrownBy(() -> {
            try (var ignored = start(database, KEY)) {
                throw new AssertionError("损坏密文不应启动成功");
            }
        }).hasStackTraceContaining("Git Token 校验失败");
    }

    @Test
    void startupDoesNotMigrateLegacyPlaintext() throws Exception {
        String database = newDatabase();
        try (var context = start(database, KEY)) {
            saveTokens(context);
        }
        jdbc(database).update("update system_settings set gitlab_token = ?", SETTINGS_TOKEN);
        jdbc(database).update("update code_repository set git_token = ?", REPO_TOKEN);
        try (var context = start(database, KEY)) {
            verifyConnections(context);
            assertThat(jdbc(database).queryForObject("select gitlab_token from system_settings", String.class))
                    .isEqualTo(SETTINGS_TOKEN);
            assertThat(jdbc(database).queryForObject("select git_token from code_repository", String.class))
                    .isEqualTo(REPO_TOKEN);
        }
    }

    private ConfigurableApplicationContext start(String database, String key) {
        return new SpringApplicationBuilder(ReleaseHubApplication.class)
                .initializers(context -> TestPropertyValues.of(Map.of(
                        "spring.datasource.url", url(database),
                        "spring.datasource.username", POSTGRES.getUsername(),
                        "spring.datasource.password", POSTGRES.getPassword(),
                        "security.jwt.secret", "isolated-crypto-jwt-key-at-least-32-bytes",
                        "releasehub.bootstrap-admin.username", "crypto-owner",
                        "releasehub.bootstrap-admin.password", "Crypto-admin-fixture-password!",
                        "releasehub.crypto.secret-key", key
                )).applyTo(context.getEnvironment()))
                .run("--spring.profiles.active=prd,real", "--server.port=0");
    }

    private void saveTokens(ConfigurableApplicationContext context) {
        context.getBean(SettingsPort.class).saveGitLab(new SettingsPort.SettingsGitLab(baseUrl(), SETTINGS_TOKEN));
        var repo = new CodeRepositoryJpaEntity();
        repo.setId("crypto-fixture-repository");
        repo.setName("加密恢复验证仓库");
        repo.setCloneUrl(baseUrl() + "/acme/repo.git");
        repo.setDefaultBranch("main");
        repo.setRepoType("SERVICE");
        repo.setGitProvider("GITLAB");
        repo.setGitAccessToken(REPO_TOKEN);
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        context.getBean(CodeRepositoryJpaRepository.class).saveAndFlush(repo);
    }

    private void verifyConnections(ConfigurableApplicationContext context) {
        assertThat(context.getBean(GitLabPort.class).testConnection()).isTrue();
        var repo = context.getBean(CodeRepositoryJpaRepository.class).findById("crypto-fixture-repository").orElseThrow();
        assertThat(repo.getGitAccessToken()).isEqualTo(REPO_TOKEN);
        assertThat(context.getBean(GitLabGitBranchAdapter.class)
                .getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), "main").exists()).isTrue();
    }

    private static String baseUrl() {
        return "http://127.0.0.1:" + gitlab.getAddress().getPort();
    }

    private static String newDatabase() throws Exception {
        String name = "crypto_" + DATABASES.incrementAndGet();
        command("createdb", "-U", POSTGRES.getUsername(), name);
        return name;
    }

    private static String url(String database) {
        return POSTGRES.getJdbcUrl().replace("/crypto_base", "/" + database);
    }

    private static JdbcTemplate jdbc(String database) {
        return new JdbcTemplate(new DriverManagerDataSource(url(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static void command(String... command) throws Exception {
        var result = POSTGRES.execInContainer(command);
        assertThat(result.getExitCode()).as("隔离数据库命令必须成功").isZero();
    }
}
