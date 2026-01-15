package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.interfaces.api.releasewindow.BatchVersionUpdateRequest;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import io.releasehub.interfaces.api.releasewindow.VersionUpdateRequest;
import io.releasehub.interfaces.api.repo.CreateRepoRequest;
import io.releasehub.interfaces.auth.AuthController.LoginRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本更新 API 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VersionUpdateApiTest {

    private static String token;
    private static String windowId;
    private static String repoId;
    private static Path testRepoPath;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupTestFiles() throws IOException {
        // 创建测试用的 pom.xml
        testRepoPath = Files.createTempDirectory("test-repo-");
        Path pomPath = testRepoPath.resolve("pom.xml");
        Files.writeString(pomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <name>Test Project</name>
                </project>
                """);
    }

    @AfterAll
    static void cleanupTestFiles() throws IOException {
        if (testRepoPath != null) {
            Files.walk(testRepoPath)
                 .sorted((a, b) -> b.compareTo(a))
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (IOException ignored) {
                     }
                 });
        }
    }

    @Test
    @Order(1)
    void shouldLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(loginRequest)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.data.token").exists())
                                  .andReturn();

        token = objectMapper.readTree(result.getResponse().getContentAsString())
                            .get("data").get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(2)
    void shouldCreateReleaseWindow() throws Exception {
        CreateReleaseWindowRequest request = new CreateReleaseWindowRequest();
        request.setName("Version Update Test Window");

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                                          .header("Authorization", "Bearer " + token)
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(request)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.data.id").exists())
                                  .andReturn();

        windowId = objectMapper.readTree(result.getResponse().getContentAsString())
                               .get("data").get("id").asText();
        assertThat(windowId).isNotBlank();
    }

    @Test
    @Order(3)
    void shouldCreateRepository() throws Exception {
        CreateRepoRequest request = new CreateRepoRequest();
        request.setName("Test Repository");
        request.setCloneUrl("git@gitlab.com:test/repo.git");
        request.setMonoRepo(false);
        request.setInitialVersion("0.1.0");

        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                                          .header("Authorization", "Bearer " + token)
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(request)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.data.id").exists())
                                  .andExpect(jsonPath("$.data.defaultBranch").value("main"))
                                  .andReturn();

        repoId = objectMapper.readTree(result.getResponse().getContentAsString())
                             .get("data").get("id").asText();
        assertThat(repoId).isNotBlank();

        mockMvc.perform(get("/api/v1/repositories/" + repoId + "/initial-version")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("0.1.0"));
    }

    @Test
    @Order(4)
    void shouldExecuteVersionUpdate() throws Exception {
        VersionUpdateRequest request = new VersionUpdateRequest();
        request.setRepoId(repoId);
        request.setBuildTool(BuildTool.MAVEN);
        request.setTargetVersion("2.0.0");
        request.setRepoPath(testRepoPath.toString());
        request.setPomPath(testRepoPath.resolve("pom.xml").toString());

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/version-update")
                                          .header("Authorization", "Bearer " + token)
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(request)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.success").value(true))
                                  .andExpect(jsonPath("$.data.runId").exists())
                                  .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                                  .andReturn();

        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                                   .get("data").get("runId").asText();
        assertThat(runId).isNotBlank();

        // 验证 pom.xml 已被更新
        String pomContent = Files.readString(testRepoPath.resolve("pom.xml"));
        assertThat(pomContent).contains("<version>2.0.0</version>");
    }

    @Test
    @Order(5)
    void shouldReturnErrorForNonExistentRepo() throws Exception {
        VersionUpdateRequest request = new VersionUpdateRequest();
        request.setRepoId("non-existent-repo-id");
        request.setBuildTool(BuildTool.MAVEN);
        request.setTargetVersion("2.0.0");
        request.setRepoPath("/tmp/non-existent");
        request.setPomPath("/tmp/non-existent/pom.xml");

        // 仓库不存在时返回 404 Not Found
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/version-update")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("REPO_001"));
    }

    @Test
    @Order(6)
    void shouldExecuteBatchVersionUpdate() throws Exception {
        // 重置 pom.xml 版本
        Files.writeString(testRepoPath.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>2.0.0</version>
                    <name>Test Project</name>
                </project>
                """);

        BatchVersionUpdateRequest request = new BatchVersionUpdateRequest();
        request.setTargetVersion("3.0.0");

        BatchVersionUpdateRequest.RepoVersionUpdate repoUpdate = new BatchVersionUpdateRequest.RepoVersionUpdate();
        repoUpdate.setRepoId(repoId);
        repoUpdate.setBuildTool(BuildTool.MAVEN);
        repoUpdate.setRepoPath(testRepoPath.toString());
        repoUpdate.setPomPath(testRepoPath.resolve("pom.xml").toString());

        request.setRepositories(List.of(repoUpdate));

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/batch-version-update")
                                          .header("Authorization", "Bearer " + token)
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(request)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.success").value(true))
                                  .andExpect(jsonPath("$.data.runId").exists())
                                  .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                                  .andReturn();

        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                                   .get("data").get("runId").asText();
        assertThat(runId).isNotBlank();

        // 验证 pom.xml 已被更新
        String pomContent = Files.readString(testRepoPath.resolve("pom.xml"));
        assertThat(pomContent).contains("<version>3.0.0</version>");
    }

    @Test
    @Order(7)
    void shouldGetRunDetails() throws Exception {
        // 执行一次版本更新以获取 runId
        Files.writeString(testRepoPath.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>3.0.0</version>
                    <name>Test Project</name>
                </project>
                """);

        VersionUpdateRequest request = new VersionUpdateRequest();
        request.setRepoId(repoId);
        request.setBuildTool(BuildTool.MAVEN);
        request.setTargetVersion("4.0.0");
        request.setRepoPath(testRepoPath.toString());
        request.setPomPath(testRepoPath.resolve("pom.xml").toString());

        MvcResult updateResult = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/version-update")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

        String runId = objectMapper.readTree(updateResult.getResponse().getContentAsString())
                                   .get("data").get("runId").asText();

        // 查询 Run 详情
        mockMvc.perform(get("/api/v1/runs/" + runId)
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true))
               .andExpect(jsonPath("$.data.id").value(runId))
               .andExpect(jsonPath("$.data.runType").value("VERSION_UPDATE"));
    }
}
