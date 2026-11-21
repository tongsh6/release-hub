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
    
    private final Map<String, FileSnapshot> files = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong revisions = new java.util.concurrent.atomic.AtomicLong();

    @Override
    public Optional<FileSnapshot> readFile(String repoCloneUrl, String branch, String filePath) {
        String key = buildKey(repoCloneUrl, branch, filePath);
        if ("pom.xml".equals(filePath)) {
            return Optional.of(files.computeIfAbsent(key, ignored -> snapshot(generateDefaultPom(repoCloneUrl))));
        }
        return Optional.ofNullable(files.get(key));
    }

    @Override
    public boolean fileExists(String repoCloneUrl, String branch, String filePath) {
        return files.containsKey(buildKey(repoCloneUrl, branch, filePath)) || "pom.xml".equals(filePath);
    }

    @Override
    public boolean updateFile(String repoCloneUrl, String branch, String filePath,
                              FileSnapshot expected, String content, String commitMessage) {
        java.util.Objects.requireNonNull(expected, "expected file snapshot");
        String key = buildKey(repoCloneUrl, branch, filePath);
        var updated = new java.util.concurrent.atomic.AtomicBoolean();
        files.compute(key, (ignored, current) -> {
            if (current == null || !current.lastCommitId().equals(expected.lastCommitId())) return current;
            updated.set(true);
            return snapshot(content);
        });
        return updated.get();
    }

    /** 设置 fixture 内容，同时推进 revision，以覆盖同内容的并发提交。 */
    public void setFile(String repoCloneUrl, String branch, String filePath, String content) {
        files.put(buildKey(repoCloneUrl, branch, filePath), snapshot(content));
    }

    private FileSnapshot snapshot(String content) {
        return new FileSnapshot(content, "%040x".formatted(revisions.incrementAndGet()));
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
