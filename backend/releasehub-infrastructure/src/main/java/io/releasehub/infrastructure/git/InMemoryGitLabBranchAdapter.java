package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "releasehub.gitlab.in-memory-branch-adapter", havingValue = "true")
public class InMemoryGitLabBranchAdapter implements GitBranchPort {

    private final Map<String, Set<String>> branches = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tags = new ConcurrentHashMap<>();

    @Override
    public boolean supports(GitProvider provider) {
        return provider == GitProvider.GITLAB;
    }

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        branchSet.add("main");
        branchSet.add("master");
        if (!branchSet.contains(fromBranch) || branchSet.contains(branchName)) {
            return false;
        }
        return branchSet.add(branchName);
    }

    @Override
    public boolean deleteBranch(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        if (branchSet == null) {
            return false;
        }
        return branchSet.remove(branchName);
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        branchSet.add("main");
        branchSet.add("master");
        if (!branchSet.contains(sourceBranch)) {
            return MergeResult.failed("source branch not found");
        }
        if (!branchSet.contains(targetBranch)) {
            return MergeResult.failed("target branch not found");
        }
        return MergeResult.success();
    }

    @Override
    public boolean createTag(String repoCloneUrl, String token, String tagName, String ref, String message) {
        Set<String> tagSet = tags.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        return tagSet.add(tagName);
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        return MergeabilityResult.mergeable();
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        if (branchSet == null) {
            return false;
        }
        log.info("Archiving branch '{}' with reason: {}", branchName, reason);
        String archivedName = "archive/" + reason + "/" + branchName;
        branchSet.add(archivedName);
        return branchSet.remove(branchName);
    }

    @Override
    public String triggerPipeline(String repoCloneUrl, String token, String ref) {
        log.info("In-memory GitLab: triggering pipeline for ref '{}' in repo {}", ref, repoCloneUrl);
        return "in-memory-pipeline-" + System.currentTimeMillis();
    }

    @Override
    public List<String> listBranches(String repoCloneUrl, String token, String prefix) {
        Set<String> branchSet = branches.getOrDefault(repoCloneUrl, Set.of());
        return branchSet.stream().filter(b -> b.startsWith(prefix)).toList();
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        boolean exists = branchSet != null && branchSet.contains(branchName);
        if (!exists && ("main".equals(branchName) || "master".equals(branchName))) {
            exists = true;
        }
        return exists ? BranchStatus.present("in-memory-latest") : BranchStatus.missing();
    }
}
