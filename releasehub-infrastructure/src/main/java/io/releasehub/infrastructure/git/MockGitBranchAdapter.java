package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        return provider == null || provider == GitProvider.MOCK;
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
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        boolean exists = branchSet != null && branchSet.contains(branchName);
        if (!exists && ("main".equals(branchName) || "master".equals(branchName))) {
            exists = true;
        }
        return exists ? BranchStatus.present("mock-latest") : BranchStatus.missing();
    }
}
