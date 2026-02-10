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
public class VersionExtractor {
    
    private final GitLabFilePort gitLabFilePort;
    
    // Maven pom.xml 中的版本号正则
    private static final Pattern POM_VERSION_PATTERN = Pattern.compile(
            "<version>([^<]+)</version>", Pattern.DOTALL);
    
    // Gradle gradle.properties 中的版本号正则
    private static final Pattern GRADLE_VERSION_PATTERN = Pattern.compile(
            "^\\s*version\\s*=\\s*([^\\s]+)", Pattern.MULTILINE);
    
    /**
     * 从仓库提取版本号
     * @return 版本号和来源
     */
    public Optional<VersionInfo> extractVersion(String repoCloneUrl, String branch) {
        // 优先尝试 Maven pom.xml
        Optional<String> pomVersion = extractFromPom(repoCloneUrl, branch);
        if (pomVersion.isPresent()) {
            return Optional.of(new VersionInfo(pomVersion.get(), VersionSource.POM));
        }
        
        // 尝试 Gradle gradle.properties
        Optional<String> gradleVersion = extractFromGradle(repoCloneUrl, branch);
        if (gradleVersion.isPresent()) {
            return Optional.of(new VersionInfo(gradleVersion.get(), VersionSource.GRADLE));
        }
        
        log.warn("Cannot extract version from repository: {} branch: {}", repoCloneUrl, branch);
        return Optional.empty();
    }
    
    /**
     * 从 pom.xml 提取版本号
     */
    private Optional<String> extractFromPom(String repoCloneUrl, String branch) {
        Optional<String> content = gitLabFilePort.readFile(repoCloneUrl, branch, "pom.xml");
        if (content.isEmpty()) {
            return Optional.empty();
        }
        
        String pomContent = content.get();
        // 提取项目自身的版本号（第一个 <version> 标签，排除 parent 中的）
        // 简化处理：查找 <project> 下直接的 <version>
        Matcher matcher = POM_VERSION_PATTERN.matcher(pomContent);
        
        // 跳过 parent 中的版本号
        int projectStart = pomContent.indexOf("<project");
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
    
    /**
     * 从 gradle.properties 提取版本号
     */
    private Optional<String> extractFromGradle(String repoCloneUrl, String branch) {
        Optional<String> content = gitLabFilePort.readFile(repoCloneUrl, branch, "gradle.properties");
        if (content.isEmpty()) {
            return Optional.empty();
        }
        
        Matcher matcher = GRADLE_VERSION_PATTERN.matcher(content.get());
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        
        return Optional.empty();
    }
    
    /**
     * 版本信息
     */
    public record VersionInfo(String version, VersionSource source) {}
}
