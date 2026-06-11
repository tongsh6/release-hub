package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MockGitBranchAdapter implements GitBranchPort {

    private final Map<String, Set<String>> branches = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tags = new ConcurrentHashMap<>();

    @Override
    public boolean supports(GitProvider provider) {
        return provider == GitProvider.MOCK;
    }

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        ensureDefaultBranches(branchSet);
        if (!branchSet.contains(fromBranch) || branchSet.contains(branchName)) {
            return false;
        }
        return branchSet.add(branchName);
    }

    @Override
    public boolean deleteBranch(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        return branchSet != null && branchSet.remove(branchName);
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        ensureDefaultBranches(branchSet);
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
        return tags.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet()).add(tagName);
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        ensureDefaultBranches(branchSet);
        boolean exists = branchSet.contains(branchName);
        return exists ? BranchStatus.present("mock-latest") : BranchStatus.missing();
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        return MergeabilityResult.mergeable();
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        if (branchSet == null || !branchSet.remove(branchName)) {
            return false;
        }
        String archivedName = "archive/" + reason + "/" + branchName;
        branchSet.add(archivedName);
        log.info("Mock Git: archived branch '{}' as '{}'", branchName, archivedName);
        return true;
    }

    @Override
    public String triggerPipeline(String repoCloneUrl, String token, String ref) {
        return "mock-pipeline-" + System.currentTimeMillis();
    }

    @Override
    public List<String> listBranches(String repoCloneUrl, String token, String prefix) {
        String branchPrefix = prefix == null ? "" : prefix;
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        ensureDefaultBranches(branchSet);
        return branchSet.stream()
                .filter(branch -> branchPrefix.isBlank() || branch.startsWith(branchPrefix))
                .toList();
    }

    private void ensureDefaultBranches(Set<String> branchSet) {
        branchSet.add("main");
        branchSet.add("master");
    }
}
