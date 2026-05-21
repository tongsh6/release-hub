package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.port.out.GitLabFilePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "releasehub.gitlab.in-memory-file-adapter", havingValue = "true")
public class InMemoryGitLabFileAdapter implements GitLabFilePort {
    
    private final Map<String, String> files = new ConcurrentHashMap<>();
    
    @Override
    public Optional<String> readFile(String repoCloneUrl, String branch, String filePath) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        String content = files.get(key);
        
        if (content != null) {
            log.debug("In-memory read file: {} - found", key);
            return Optional.of(content);
        }
        
        if ("pom.xml".equals(filePath)) {
            log.debug("In-memory read file: {} - returning default pom.xml", key);
            return Optional.of(generateDefaultPom(repoCloneUrl));
        }
        
        log.debug("In-memory read file: {} - not found", key);
        return Optional.empty();
    }
    
    @Override
    public boolean fileExists(String repoCloneUrl, String branch, String filePath) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        if (files.containsKey(key)) {
            return true;
        }
        return "pom.xml".equals(filePath);
    }

    @Override
    public boolean updateFile(String repoCloneUrl, String branch, String filePath, String content, String commitMessage) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        files.put(key, content);
        log.info("In-memory update file: {} - success, commitMessage: {}", key, commitMessage);
        return true;
    }
    
    /**
     * 设置文件内容。
     */
    public void setFile(String repoCloneUrl, String branch, String filePath, String content) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        files.put(key, content);
    }
    
    public void clearFiles() {
        files.clear();
    }
    
    private String buildKey(String repoCloneUrl, String branch, String filePath) {
        return repoCloneUrl + ":" + branch + ":" + filePath;
    }
    
    private String generateDefaultPom(String repoCloneUrl) {
        int hash = Math.abs(repoCloneUrl.hashCode() % 10);
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>${artifactId}</artifactId>
                <version>1.${hash}.0</version>
            </project>
            """
                .replace("${artifactId}", extractArtifactId(repoCloneUrl))
                .replace("${hash}", String.valueOf(hash));
    }
    
    private String extractArtifactId(String repoCloneUrl) {
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
