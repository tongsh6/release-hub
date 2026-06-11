package io.releasehub.application.version;

import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.domain.version.VersionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本号提取器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionExtractor implements VersionExtractorUseCase {
    private final GitLabFilePort gitLabFilePort;

    // Maven pom.xml 中的版本号正则
    private static final Pattern POM_VERSION_PATTERN = Pattern.compile(
            "<version>([^<]+)</version>", Pattern.DOTALL);

    // Gradle gradle.properties 中的版本号正则
    private static final Pattern GRADLE_VERSION_PATTERN = Pattern.compile(
            "^\\s*version\\s*=\\s*([^\\s]+)", Pattern.MULTILINE);

    private static final Pattern VERSION_VALUE_PATTERN = Pattern.compile(
            "^[0-9][0-9A-Za-z._+\\-]*$");
    
    /**
     * 从仓库提取版本号
     * @return 版本号和来源
     */
    public Optional<VersionExtractorUseCase.VersionInfo> extractVersion(String repoCloneUrl, String branch) {
        VersionInspection inspection = inspectVersion(repoCloneUrl, branch);
        if (inspection.status() == VersionInspectionStatus.RESOLVED) {
            return Optional.of(new VersionExtractorUseCase.VersionInfo(inspection.version(), inspection.source()));
        }
        return Optional.empty();
    }

    @Override
    public VersionInspection inspectVersion(String repoCloneUrl, String branch) {
        Optional<String> pomContent;
        try {
            pomContent = gitLabFilePort.readFile(repoCloneUrl, branch, "pom.xml");
        } catch (Exception e) {
            log.warn("Failed to read pom.xml from repository: {} branch: {}: {}", repoCloneUrl, branch, e.getMessage());
            return VersionInspection.unresolved(
                    VersionInspectionError.VERSION_READ_ERROR,
                    branch,
                    java.util.List.of("pom.xml"),
                    "读取 pom.xml 失败: " + e.getMessage()
            );
        }

        if (pomContent.isPresent()) {
            Optional<String> pomVersion = extractFromPomContent(pomContent.get());
            if (pomVersion.isPresent()) {
                String version = pomVersion.get();
                if (!isSupportedVersionValue(version)) {
                    return VersionInspection.unresolved(
                            VersionInspectionError.VERSION_INVALID,
                            branch,
                            java.util.List.of("pom.xml"),
                            "pom.xml 中的版本号格式异常: " + version
                    );
                }
                return VersionInspection.resolved(version, VersionSource.POM, branch, java.util.List.of("pom.xml"));
            }
        }

        Optional<String> gradleContent;
        try {
            gradleContent = gitLabFilePort.readFile(repoCloneUrl, branch, "gradle.properties");
        } catch (Exception e) {
            log.warn("Failed to read gradle.properties from repository: {} branch: {}: {}", repoCloneUrl, branch, e.getMessage());
            return VersionInspection.unresolved(
                    VersionInspectionError.VERSION_READ_ERROR,
                    branch,
                    java.util.List.of("pom.xml", "gradle.properties"),
                    "读取 gradle.properties 失败: " + e.getMessage()
            );
        }

        if (gradleContent.isPresent()) {
            Optional<String> gradleVersion = extractFromGradleContent(gradleContent.get());
            if (gradleVersion.isPresent()) {
                String version = gradleVersion.get();
                if (!isSupportedVersionValue(version)) {
                    return VersionInspection.unresolved(
                            VersionInspectionError.VERSION_INVALID,
                            branch,
                            java.util.List.of("pom.xml", "gradle.properties"),
                            "gradle.properties 中的版本号格式异常: " + version
                    );
                }
                return VersionInspection.resolved(version, VersionSource.GRADLE, branch, java.util.List.of("pom.xml", "gradle.properties"));
            }
            return VersionInspection.unresolved(
                    VersionInspectionError.VERSION_DECL_MISSING,
                    branch,
                    java.util.List.of("pom.xml", "gradle.properties"),
                    "版本文件存在，但未找到项目版本号声明"
            );
        }

        if (pomContent.isPresent()) {
            return VersionInspection.unresolved(
                    VersionInspectionError.VERSION_DECL_MISSING,
                    branch,
                    java.util.List.of("pom.xml", "gradle.properties"),
                    "pom.xml 存在，但未找到项目自身的 <version> 声明"
            );
        }

        log.warn("Cannot find version file in repository: {} branch: {}", repoCloneUrl, branch);
        return VersionInspection.unresolved(
                VersionInspectionError.VERSION_FILE_MISSING,
                branch,
                java.util.List.of("pom.xml", "gradle.properties"),
                "未找到 pom.xml 或 gradle.properties"
        );
    }

    private Optional<String> extractFromPomContent(String pomContent) {
        // 提取项目自身的版本号（第一个 <version> 标签，排除 parent 中的）
        // 简化处理：查找 <project> 下直接的 <version>
        Matcher matcher = POM_VERSION_PATTERN.matcher(pomContent);
        
        // 跳过 parent 中的版本号
        int parentStart = pomContent.indexOf("<parent");
        int parentEnd = pomContent.indexOf("</parent>");
        
        while (matcher.find()) {
            int pos = matcher.start();
            // 如果在 parent 标签内，跳过
            if (parentStart != -1 && parentEnd != -1 && pos > parentStart && pos < parentEnd) {
                continue;
            }
            return Optional.of(matcher.group(1).trim());
        }
        
        return Optional.empty();
    }
    
    private Optional<String> extractFromGradleContent(String gradleContent) {
        Matcher matcher = GRADLE_VERSION_PATTERN.matcher(gradleContent);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        
        return Optional.empty();
    }

    private boolean isSupportedVersionValue(String version) {
        return version != null && VERSION_VALUE_PATTERN.matcher(version).matches();
    }
    
    /**
     * 版本信息
     */
    // 使用接口中的 VersionInfo 记录类
}
