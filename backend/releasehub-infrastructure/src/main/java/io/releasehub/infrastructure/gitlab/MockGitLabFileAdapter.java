package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.port.out.GitLabFilePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟 GitLab 文件读取适配器
 * 用于开发和测试环境
 */
@Slf4j
@Component
public class MockGitLabFileAdapter implements GitLabFilePort {
    
    // 模拟文件存储: key = "repoUrl:branch:filePath", value = content
    private final Map<String, String> mockFiles = new ConcurrentHashMap<>();
    
    public MockGitLabFileAdapter() {
        // 预置一些模拟数据
        initMockData();
    }
    
    private void initMockData() {
        // 模拟一个 Maven 项目的 pom.xml
        String mockPom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo-project</artifactId>
                <version>1.0.0</version>
            </project>
            """;
        
        // 默认情况下，所有仓库的 master 分支都返回这个模拟版本
        // 实际使用时可以通过 setMockFile 方法设置具体仓库的文件内容
    }
    
    @Override
    public Optional<String> readFile(String repoCloneUrl, String branch, String filePath) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        String content = mockFiles.get(key);
        
        if (content != null) {
            log.debug("Mock read file: {} - found", key);
            return Optional.of(content);
        }
        
        // 如果没有找到具体的模拟文件，返回默认的 pom.xml
        if ("pom.xml".equals(filePath)) {
            log.debug("Mock read file: {} - returning default pom.xml", key);
            return Optional.of(generateDefaultPom(repoCloneUrl));
        }
        
        log.debug("Mock read file: {} - not found", key);
        return Optional.empty();
    }
    
    @Override
    public boolean fileExists(String repoCloneUrl, String branch, String filePath) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        if (mockFiles.containsKey(key)) {
            return true;
        }
        // 默认 pom.xml 存在
        return "pom.xml".equals(filePath);
    }
    
    /**
     * 设置模拟文件内容（用于测试）
     */
    public void setMockFile(String repoCloneUrl, String branch, String filePath, String content) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        mockFiles.put(key, content);
    }
    
    /**
     * 清除模拟数据（用于测试）
     */
    public void clearMockData() {
        mockFiles.clear();
    }
    
    private String buildKey(String repoCloneUrl, String branch, String filePath) {
        return repoCloneUrl + ":" + branch + ":" + filePath;
    }
    
    private String generateDefaultPom(String repoCloneUrl) {
        // 根据仓库 URL 生成一个随机但确定的版本号
        int hash = Math.abs(repoCloneUrl.hashCode() % 10);
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>%s</artifactId>
                <version>1.%d.0</version>
            </project>
            """, extractArtifactId(repoCloneUrl), hash);
    }
    
    private String extractArtifactId(String repoCloneUrl) {
        // 从 URL 提取项目名作为 artifactId
        String name = repoCloneUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name.isEmpty() ? "unknown-project" : name;
    }
}
