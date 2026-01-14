package io.releasehub.infrastructure.version;

import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.BuildTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradle VersionUpdater 单元测试
 */
class GradleVersionUpdaterTest {

    @TempDir
    Path tempDir;
    private GradleVersionUpdaterAdapter updater;

    @BeforeEach
    void setUp() {
        updater = new GradleVersionUpdaterAdapter();
    }

    @Test
    void should_support_gradle() {
        assertTrue(updater.supports(BuildTool.GRADLE));
        assertFalse(updater.supports(BuildTool.MAVEN));
    }

    @Test
    void should_update_gradle_properties_successfully() throws IOException {
        // 创建测试 gradle.properties
        String propertiesContent = """
                version=1.0.0
                group=com.example
                """;

        Path propertiesFile = tempDir.resolve("gradle.properties");
        Files.writeString(propertiesFile, propertiesContent);

        VersionUpdateRequest request = VersionUpdateRequest.forGradle(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                propertiesFile.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertTrue(result.success());
        assertEquals("1.0.0", result.oldVersion());
        assertEquals("1.2.3", result.newVersion());
        assertNotNull(result.diff());
        assertTrue(result.diff().contains("1.0.0"));
        assertTrue(result.diff().contains("1.2.3"));

        // 验证文件已更新
        String updatedContent = Files.readString(propertiesFile);
        assertTrue(updatedContent.contains("version=1.2.3") || updatedContent.contains("version = 1.2.3"));
        assertFalse(updatedContent.contains("version=1.0.0"));
    }

    @Test
    void should_fail_when_properties_not_found() {
        VersionUpdateRequest request = VersionUpdateRequest.forGradle(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                tempDir.resolve("nonexistent.properties").toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void should_fail_when_version_not_in_properties() throws IOException {
        // 创建没有 version 的 gradle.properties
        String propertiesContent = """
                group=com.example
                """;

        Path propertiesFile = tempDir.resolve("gradle.properties");
        Files.writeString(propertiesFile, propertiesContent);

        VersionUpdateRequest request = VersionUpdateRequest.forGradle(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                propertiesFile.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("未找到 version 属性") || result.errorMessage().contains("Version property not found"));
    }

    @Test
    void should_fail_when_build_gradle_has_version() throws IOException {
        // 创建 build.gradle 文件（包含版本定义）
        String buildGradleContent = """
                version = '1.0.0'
                group = 'com.example'
                """;

        Path buildGradleFile = tempDir.resolve("build.gradle");
        Files.writeString(buildGradleFile, buildGradleContent);

        // 不创建 gradle.properties
        VersionUpdateRequest request = VersionUpdateRequest.forGradle(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                null
        );

        VersionUpdateResult result = updater.update(request);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("build.gradle") ||
                result.errorMessage().contains("not supported"));
    }

    @Test
    void should_handle_version_with_spaces() throws IOException {
        // 创建带空格的版本定义
        String propertiesContent = """
                version = 1.0.0
                group = com.example
                """;

        Path propertiesFile = tempDir.resolve("gradle.properties");
        Files.writeString(propertiesFile, propertiesContent);

        VersionUpdateRequest request = VersionUpdateRequest.forGradle(
                RepoId.newId(),
                tempDir.toString(),
                "2.0.0",
                propertiesFile.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertTrue(result.success());
        assertEquals("1.0.0", result.oldVersion());
        assertEquals("2.0.0", result.newVersion());

        // 验证格式保持（带空格）
        String updatedContent = Files.readString(propertiesFile);
        assertTrue(updatedContent.contains("version = 2.0.0"));
    }
}
