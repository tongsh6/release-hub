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
 * Maven VersionUpdater 单元测试
 */
class MavenVersionUpdaterTest {

    @TempDir
    Path tempDir;
    private MavenVersionUpdaterAdapter updater;

    @BeforeEach
    void setUp() {
        updater = new MavenVersionUpdaterAdapter();
    }

    @Test
    void should_support_maven() {
        assertTrue(updater.supports(BuildTool.MAVEN));
        assertFalse(updater.supports(BuildTool.GRADLE));
    }

    @Test
    void should_update_version_successfully() throws IOException {
        // 创建测试 pom.xml
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        VersionUpdateRequest request = VersionUpdateRequest.forMaven(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                pomFile.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertTrue(result.success());
        assertEquals("1.0.0", result.oldVersion());
        assertEquals("1.2.3", result.newVersion());
        assertNotNull(result.diff());
        assertTrue(result.diff().contains("1.0.0"));
        assertTrue(result.diff().contains("1.2.3"));

        // 验证文件已更新
        String updatedContent = Files.readString(pomFile);
        assertTrue(updatedContent.contains("<version>1.2.3</version>"));
        assertFalse(updatedContent.contains("<version>1.0.0</version>"));
    }

    @Test
    void should_fail_when_pom_not_found() {
        VersionUpdateRequest request = VersionUpdateRequest.forMaven(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                tempDir.resolve("nonexistent.xml").toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("不存在") || result.errorMessage().contains("not found"));
    }

    @Test
    void should_fail_when_version_not_found() throws IOException {
        // 创建没有 version 的 pom.xml
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                </project>
                """;

        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        VersionUpdateRequest request = VersionUpdateRequest.forMaven(
                RepoId.newId(),
                tempDir.toString(),
                "1.2.3",
                pomFile.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void should_sync_parent_and_child_versions_for_multi_module_project() throws IOException {
        Path parentPom = tempDir.resolve("pom.xml");
        Path module1Dir = Files.createDirectories(tempDir.resolve("module1"));
        Path module2Dir = Files.createDirectories(tempDir.resolve("module2"));
        Path module1Pom = module1Dir.resolve("pom.xml");
        Path module2Pom = module2Dir.resolve("pom.xml");

        Files.writeString(parentPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>module1</module>
                        <module>module2</module>
                    </modules>
                </project>
                """);

        Files.writeString(module1Pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>module1</artifactId>
                    <version>1.0.0</version>
                </project>
                """);

        Files.writeString(module2Pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>module2</artifactId>
                    <version>0.9.0</version>
                </project>
                """);

        VersionUpdateRequest request = VersionUpdateRequest.forMaven(
                RepoId.newId(),
                tempDir.toString(),
                "2.0.0",
                parentPom.toString()
        );

        VersionUpdateResult result = updater.update(request);

        assertTrue(result.success());
        assertEquals("1.0.0", result.oldVersion());
        assertEquals("2.0.0", result.newVersion());

        String parentContent = Files.readString(parentPom);
        assertTrue(parentContent.contains("<version>2.0.0</version>"));

        String module1Content = Files.readString(module1Pom);
        assertTrue(module1Content.contains("<parent>"));
        assertTrue(module1Content.contains("<version>2.0.0</version>"));
        assertFalse(module1Content.contains("<version>1.0.0</version>"));

        String module2Content = Files.readString(module2Pom);
        assertTrue(module2Content.contains("<parent>"));
        assertTrue(module2Content.contains("<version>2.0.0</version>"));
        assertTrue(module2Content.contains("<version>0.9.0</version>"));
    }
}
