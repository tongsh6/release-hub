package io.releasehub.infrastructure.version;

import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.common.exception.BizException;
import io.releasehub.domain.version.BuildTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
public class GradleVersionUpdaterAdapter implements VersionUpdaterPort {

    @Override
    public VersionUpdateResult update(VersionUpdateRequest request) {
        try {
            // 优先使用 gradle.properties
            String gradlePropertiesPath = request.gradlePropertiesPath() != null
                    ? request.gradlePropertiesPath()
                    : Paths.get(request.repoPath(), "gradle.properties").toString();
            
            Path gradlePropertiesFile = Paths.get(gradlePropertiesPath);
            
            // 检查 gradle.properties 是否存在
            if (Files.exists(gradlePropertiesFile)) {
                return updateGradleProperties(gradlePropertiesFile, request.targetVersion());
            }
            
            // 如果 gradle.properties 不存在，检查 build.gradle
            Path buildGradleFile = Paths.get(request.repoPath(), "build.gradle");
            if (Files.exists(buildGradleFile)) {
                // 检查 build.gradle 中是否有版本定义
                String buildGradleContent = Files.readString(buildGradleFile);
                if (containsVersionDefinition(buildGradleContent)) {
                    return VersionUpdateResult.failure(
                            "检测到版本定义在 build.gradle 中，当前版本不支持此场景。\n" +
                            "建议：将版本定义迁移到 gradle.properties 文件中（格式：version=1.0.0），或手动更新版本。",
                            buildGradleFile.toString()
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
            
        } catch (BizException e) {
            return VersionUpdateResult.failure(e.getMessage(), request.gradlePropertiesPath());
        } catch (Exception e) {
            log.error("Failed to update Gradle version", e);
            return VersionUpdateResult.failure(
                    "Failed to update version: " + e.getMessage(),
                    request.gradlePropertiesPath()
            );
        }
    }

    @Override
    public boolean supports(BuildTool buildTool) {
        return buildTool == BuildTool.GRADLE;
    }

    /**
     * 更新 gradle.properties 文件中的版本
     */
    private VersionUpdateResult updateGradleProperties(Path gradlePropertiesFile, String newVersion) throws IOException {
        // 读取原始内容
        String originalContent = Files.readString(gradlePropertiesFile);
        
        // 解析 Properties
        Properties properties = new Properties();
        properties.load(Files.newInputStream(gradlePropertiesFile));
        
        String oldVersion = properties.getProperty("version");
            if (oldVersion == null) {
                return VersionUpdateResult.failure(
                        "gradle.properties 文件中未找到 version 属性。请确保文件包含 'version=...' 配置。",
                        gradlePropertiesFile.toString()
                );
            }
        
        // 更新版本
        properties.setProperty("version", newVersion);
        
        // 生成更新后的内容
        StringBuilder updatedContent = new StringBuilder();
        List<String> originalLines = Files.readAllLines(gradlePropertiesFile);
        
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
        Files.writeString(gradlePropertiesFile, updatedContent.toString());
        
        log.info("Updated Gradle version from {} to {} in {}", oldVersion, newVersion, gradlePropertiesFile);
        
        return VersionUpdateResult.success(oldVersion, newVersion, diff, gradlePropertiesFile.toString());
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
