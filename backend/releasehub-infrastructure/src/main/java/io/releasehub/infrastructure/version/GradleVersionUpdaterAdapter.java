package io.releasehub.infrastructure.version;

import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.common.exception.BaseException;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.version.BuildTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Gradle 版本更新器实现
 * <p>
 * MVP 阶段优先支持 gradle.properties 文件中的版本更新。
 * 如果版本定义在 build.gradle 中，返回明确的错误提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradleVersionUpdaterAdapter implements VersionUpdaterPort {

    private final GitLabFilePort gitLabFilePort;
    private final CodeRepositoryPort codeRepositoryPort;

    @Override
    public VersionUpdateResult update(VersionUpdateRequest request) {
        try {
            FileOperator operator = getOperator(request);
            
            // 优先使用 gradle.properties
            String gradlePropertiesPath = request.gradlePropertiesPath() != null
                    ? request.gradlePropertiesPath()
                    : (request.repoPath() != null && !request.repoPath().equals(".") 
                        ? Paths.get(request.repoPath(), "gradle.properties").toString()
                        : "gradle.properties");
            
            // 检查 gradle.properties 是否存在
            if (operator.exists(gradlePropertiesPath)) {
                return updateGradleProperties(operator, gradlePropertiesPath, request.targetVersion());
            }
            
            // 如果 gradle.properties 不存在，检查 build.gradle
            String buildGradlePath = (request.repoPath() != null && !request.repoPath().equals(".")
                    ? Paths.get(request.repoPath(), "build.gradle").toString()
                    : "build.gradle");
            if (operator.exists(buildGradlePath)) {
                // 检查 build.gradle 中是否有版本定义
                String buildGradleContent = operator.read(buildGradlePath);
                if (containsVersionDefinition(buildGradleContent)) {
                    return VersionUpdateResult.failure(
                            "检测到版本定义在 build.gradle 中，当前版本不支持此场景。\n" +
                            "建议：将版本定义迁移到 gradle.properties 文件中（格式：version=1.0.0），或手动更新版本。",
                            buildGradlePath
                    );
                }
            }
            
            // 如果都不存在，返回错误
            return VersionUpdateResult.failure(
                    String.format(
                            "在仓库路径中未找到 gradle.properties 或 build.gradle 文件: %s\n" +
                            "请确保路径正确，或手动创建 gradle.properties 文件。",
                            request.repoPath()
                    ),
                    request.repoPath()
            );
            
        } catch (BaseException e) {
            return VersionUpdateResult.failure(e.getMessage(), request.gradlePropertiesPath());
        } catch (Exception e) {
            log.error("Failed to update Gradle version", e);
            return VersionUpdateResult.failure(
                    "Failed to update version: " + e.getMessage(),
                    request.gradlePropertiesPath()
            );
        }
    }

    private FileOperator getOperator(VersionUpdateRequest request) {
        if (request.branchName() != null) {
            CodeRepository repo = codeRepositoryPort.findById(request.repoId()).orElse(null);
            if (repo != null && repo.getCloneUrl() != null) {
                return new RemoteFileOperator(gitLabFilePort, repo.getCloneUrl(), request.branchName());
            }
        }
        return new LocalFileOperator();
    }

    private interface FileOperator {
        String read(String path) throws Exception;
        void write(String path, String content) throws Exception;
        boolean exists(String path);
    }

    @RequiredArgsConstructor
    private static class RemoteFileOperator implements FileOperator {
        private final GitLabFilePort gitLabFilePort;
        private final String repoCloneUrl;
        private final String branch;

        @Override
        public String read(String path) {
            return gitLabFilePort.readFile(repoCloneUrl, branch, path)
                    .orElseThrow(() -> new RuntimeException("File not found on GitLab: " + path));
        }

        @Override
        public void write(String path, String content) {
            boolean ok = gitLabFilePort.updateFile(repoCloneUrl, branch, path, content, "ReleaseHub: Update version");
            if (!ok) throw new RuntimeException("Failed to update file on GitLab: " + path);
        }

        @Override
        public boolean exists(String path) {
            return gitLabFilePort.fileExists(repoCloneUrl, branch, path);
        }
    }

    private static class LocalFileOperator implements FileOperator {
        @Override
        public String read(String path) throws Exception {
            return Files.readString(Paths.get(path));
        }

        @Override
        public void write(String path, String content) throws Exception {
            Files.writeString(Paths.get(path), content);
        }

        @Override
        public boolean exists(String path) {
            return Files.exists(Paths.get(path));
        }
    }

    @Override
    public boolean supports(BuildTool buildTool) {
        return buildTool == BuildTool.GRADLE;
    }

    /**
     * 更新 gradle.properties 文件中的版本
     */
    private VersionUpdateResult updateGradleProperties(FileOperator operator, String gradlePropertiesPath, String newVersion) throws Exception {
        // 读取原始内容
        String originalContent = operator.read(gradlePropertiesPath);
        
        // 解析 Properties
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(originalContent.getBytes(StandardCharsets.UTF_8)));
        
        String oldVersion = properties.getProperty("version");
        if (oldVersion == null) {
            return VersionUpdateResult.failure(
                    "gradle.properties 文件中未找到 version 属性。请确保文件包含 'version=...' 配置。",
                    gradlePropertiesPath
            );
        }
        
        // 生成更新后的内容
        StringBuilder updatedContent = new StringBuilder();
        String[] originalLines = originalContent.split("\\r?\\n");
        
        for (String line : originalLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("version=") || trimmed.startsWith("version =")) {
                // 保持原有的格式（等号前后可能有空格）
                if (trimmed.contains(" = ")) {
                    updatedContent.append("version = ").append(newVersion).append("\n");
                } else {
                    updatedContent.append("version=").append(newVersion).append("\n");
                }
            } else {
                updatedContent.append(line).append("\n");
            }
        }
        
        // 生成 diff
        String diff = generateDiff(originalContent, updatedContent.toString());
        
        // 写回文件
        operator.write(gradlePropertiesPath, updatedContent.toString());
        
        log.info("Updated Gradle version from {} to {} in {}", oldVersion, newVersion, gradlePropertiesPath);
        
        return VersionUpdateResult.success(oldVersion, newVersion, diff, gradlePropertiesPath);
    }

    /**
     * 检查 build.gradle 中是否包含版本定义
     */
    private boolean containsVersionDefinition(String content) {
        // 简单的检查：查找 version = 或 version= 模式
        return content.matches("(?s).*version\\s*[=:]\\s*['\"].*['\"].*");
    }

    /**
     * 生成简单的 diff（行级别对比）
     */
    private String generateDiff(String oldContent, String newContent) {
        List<String> oldLines = List.of(oldContent.split("\n"));
        List<String> newLines = List.of(newContent.split("\n"));
        
        StringBuilder diff = new StringBuilder();
        int maxLines = Math.max(oldLines.size(), newLines.size());
        
        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            
            if (oldLine != null && newLine != null) {
                if (!oldLine.equals(newLine)) {
                    diff.append(String.format("@@ -%d +%d @@\n", i + 1, i + 1));
                    diff.append("-").append(oldLine).append("\n");
                    diff.append("+").append(newLine).append("\n");
                }
            } else if (oldLine != null) {
                diff.append(String.format("@@ -%d +%d @@\n", i + 1, i));
                diff.append("-").append(oldLine).append("\n");
            } else if (newLine != null) {
                diff.append(String.format("@@ -%d +%d @@\n", i, i + 1));
                diff.append("+").append(newLine).append("\n");
            }
        }
        
        return diff.toString();
    }
}
