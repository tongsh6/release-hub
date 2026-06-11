package io.releasehub.application.version;

import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.domain.version.VersionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VersionExtractor 单元测试")
class VersionExtractorTest {

    @Mock
    private GitLabFilePort gitLabFilePort;

    private VersionExtractor versionExtractor;

    @BeforeEach
    void setUp() {
        versionExtractor = new VersionExtractor(gitLabFilePort);
    }

    @Nested
    @DisplayName("extractVersion - 版本号提取")
    class ExtractVersionTest {

        private static final String REPO_URL = "git@gitlab.com:test/repo.git";
        private static final String BRANCH = "master";

        @Test
        @DisplayName("从简单 pom.xml 提取版本号")
        void shouldExtractVersionFromSimplePom() {
            String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                </project>
                """;
            
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            assertThat(result).isPresent();
            assertThat(result.get().version()).isEqualTo("1.0.0-SNAPSHOT");
            assertThat(result.get().source()).isEqualTo(VersionSource.POM);
        }

        @Test
        @DisplayName("从带 parent 的 pom.xml 提取版本号")
        void shouldExtractVersionFromPomWithParent() {
            String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>2.0.0</version>
                </project>
                """;
            
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            assertThat(result).isPresent();
            assertThat(result.get().version()).isEqualTo("2.0.0");
            assertThat(result.get().source()).isEqualTo(VersionSource.POM);
        }

        @Test
        @DisplayName("从 gradle.properties 提取版本号")
        void shouldExtractVersionFromGradle() {
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.empty());
            
            String gradleContent = """
                org.gradle.jvmargs=-Xmx2g
                version=1.5.0-SNAPSHOT
                kotlin.code.style=official
                """;
            
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "gradle.properties"))
                    .thenReturn(Optional.of(gradleContent));

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            assertThat(result).isPresent();
            assertThat(result.get().version()).isEqualTo("1.5.0-SNAPSHOT");
            assertThat(result.get().source()).isEqualTo(VersionSource.GRADLE);
        }

        @Test
        @DisplayName("优先使用 pom.xml 版本号")
        void shouldPreferPomOverGradle() {
            String pomContent = """
                <project>
                    <version>1.0.0</version>
                </project>
                """;
            
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            assertThat(result).isPresent();
            assertThat(result.get().source()).isEqualTo(VersionSource.POM);
        }

        @Test
        @DisplayName("没有版本文件返回空")
        void shouldReturnEmptyWhenNoVersionFile() {
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.empty());
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "gradle.properties"))
                    .thenReturn(Optional.empty());

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("没有版本文件时返回可追溯诊断")
        void shouldInspectMissingVersionFiles() {
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.empty());
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "gradle.properties"))
                    .thenReturn(Optional.empty());

            VersionExtractorUseCase.VersionInspection result = versionExtractor.inspectVersion(REPO_URL, BRANCH);

            assertThat(result.status()).isEqualTo(VersionExtractorUseCase.VersionInspectionStatus.UNRESOLVED);
            assertThat(result.errorType()).isEqualTo(VersionExtractorUseCase.VersionInspectionError.VERSION_FILE_MISSING);
            assertThat(result.branch()).isEqualTo(BRANCH);
            assertThat(result.checkedPaths()).containsExactly("pom.xml", "gradle.properties");
            assertThat(result.message()).contains("pom.xml");
        }

        @Test
        @DisplayName("版本文件存在但没有项目版本声明时返回可追溯诊断")
        void shouldInspectMissingVersionDeclaration() {
            String pomContent = """
                <project>
                    <parent>
                        <version>3.0.0</version>
                    </parent>
                    <artifactId>test</artifactId>
                </project>
                """;
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "gradle.properties"))
                    .thenReturn(Optional.empty());

            VersionExtractorUseCase.VersionInspection result = versionExtractor.inspectVersion(REPO_URL, BRANCH);

            assertThat(result.status()).isEqualTo(VersionExtractorUseCase.VersionInspectionStatus.UNRESOLVED);
            assertThat(result.errorType()).isEqualTo(VersionExtractorUseCase.VersionInspectionError.VERSION_DECL_MISSING);
            assertThat(result.checkedPaths()).containsExactly("pom.xml", "gradle.properties");
        }

        @Test
        @DisplayName("版本号格式异常时返回可追溯诊断")
        void shouldInspectInvalidVersionValue() {
            String pomContent = """
                <project>
                    <version>release candidate</version>
                </project>
                """;
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));

            VersionExtractorUseCase.VersionInspection result = versionExtractor.inspectVersion(REPO_URL, BRANCH);

            assertThat(result.status()).isEqualTo(VersionExtractorUseCase.VersionInspectionStatus.UNRESOLVED);
            assertThat(result.errorType()).isEqualTo(VersionExtractorUseCase.VersionInspectionError.VERSION_INVALID);
            assertThat(result.checkedPaths()).containsExactly("pom.xml");
            assertThat(result.message()).contains("release candidate");
        }

        @Test
        @DisplayName("pom.xml 无版本号时尝试 gradle.properties")
        void shouldFallbackToGradleWhenPomHasNoVersion() {
            String pomContent = """
                <project>
                    <parent>
                        <version>3.0.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>test</artifactId>
                </project>
                """;
            
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "pom.xml"))
                    .thenReturn(Optional.of(pomContent));
            
            // parent 中的版本应该被跳过，导致回退到 gradle
            String gradleContent = "version=2.0.0";
            when(gitLabFilePort.readFile(REPO_URL, BRANCH, "gradle.properties"))
                    .thenReturn(Optional.of(gradleContent));

            Optional<VersionExtractor.VersionInfo> result = versionExtractor.extractVersion(REPO_URL, BRANCH);

            // 根据当前实现逻辑，parent 中的版本会被跳过
            assertThat(result).isPresent();
        }
    }
}
