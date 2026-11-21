package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;

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
    private final Map<String, Map<String, TagEvidence>> tags = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong pipelineIds = new java.util.concurrent.atomic.AtomicLong();

    // Test-only repositories have a single synthetic commit; no remote Git state is implied.
    private static final String FIXTURE_COMMIT_SHA = "1".repeat(40);
    private final Map<String, String> projectIds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong projectSequence = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong mergeRequestIds = new java.util.concurrent.atomic.AtomicLong();
    private final Map<String, Map<List<String>, MrEvidence>> mergeRequests = new ConcurrentHashMap<>();

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
        String projectId = projectId(repoCloneUrl);
        String iid = String.valueOf(mergeRequestIds.incrementAndGet());
        var created = new MrEvidence(projectId, sourceBranch, targetBranch, iid,
                "https://mock.invalid/projects/" + projectId + "/merge_requests/" + iid,
                FIXTURE_COMMIT_SHA, FIXTURE_COMMIT_SHA, MrDisposition.CREATED, null);
        var previous = mergeRequests.computeIfAbsent(repoCloneUrl, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(List.of(sourceBranch, targetBranch), created);
        var evidence = previous == null ? created : new MrEvidence(previous.projectId(), sourceBranch, targetBranch,
                previous.iid(), previous.url(), previous.sourceSha(), previous.mergeSha(), MrDisposition.ALREADY_MERGED, null);
        return MergeResult.success().withEvidence(evidence);
    }

    @Override
    public GitReadResult<TagEvidence> readTag(String repoCloneUrl, String token, String expectedProjectId, String tagName) {
        if (!projectId(repoCloneUrl).equals(expectedProjectId)) {
            return new GitReadResult<>(GitReadResult.Status.MISMATCH, null);
        }
        TagEvidence evidence = tags.getOrDefault(repoCloneUrl, Map.of()).get(tagName);
        if (evidence == null) return new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null);
        return expectedProjectId.equals(evidence.projectId()) ? GitReadResult.found(evidence)
                : new GitReadResult<>(GitReadResult.Status.MISMATCH, null);
    }

    @Override
    public GitWriteResult<TagEvidence> createTag(String repoCloneUrl, String token, String expectedProjectId,
                                                String tagName, String targetSha, String message) {
        if (!projectId(repoCloneUrl).equals(expectedProjectId)) return GitWriteResult.rejected("PROJECT_IDENTITY_MISMATCH");
        if (!FIXTURE_COMMIT_SHA.equals(targetSha)) return GitWriteResult.rejected("COMMIT_NOT_FOUND");
        if (tagName == null || tagName.isBlank()) return GitWriteResult.rejected("TAG_NAME_REQUIRED");
        var evidence = new TagEvidence(expectedProjectId, tagName, targetSha);
        var previous = tags.computeIfAbsent(repoCloneUrl, k -> new ConcurrentHashMap<>()).putIfAbsent(tagName, evidence);
        return previous == null ? GitWriteResult.applied(evidence) : GitWriteResult.rejected("TAG_ALREADY_EXISTS");
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.computeIfAbsent(repoCloneUrl, k -> ConcurrentHashMap.newKeySet());
        ensureDefaultBranches(branchSet);
        boolean exists = branchSet.contains(branchName);
        return exists ? BranchStatus.present(FIXTURE_COMMIT_SHA) : BranchStatus.missing();
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
    public GitWriteResult<PipelineEvidence> triggerPipeline(String repoCloneUrl, String token, String expectedProjectId,
                                                            String ref, String expectedSha) {
        if (!projectId(repoCloneUrl).equals(expectedProjectId)) return GitWriteResult.rejected("PROJECT_IDENTITY_MISMATCH");
        if (ref == null || ref.isBlank()) return GitWriteResult.rejected("REF_NOT_FOUND");
        var tag = tags.getOrDefault(repoCloneUrl, Map.of()).get(ref);
        var branch = tag == null ? getBranchStatus(repoCloneUrl, token, ref) : null;
        String actualSha = tag != null ? tag.commitSha() : branch.exists() ? branch.latestCommit() : null;
        if (actualSha == null) return GitWriteResult.rejected("REF_NOT_FOUND");
        if (!actualSha.equals(expectedSha)) return GitWriteResult.rejected("REF_COMMIT_MISMATCH");
        String id = String.valueOf(pipelineIds.incrementAndGet());
        return GitWriteResult.applied(new PipelineEvidence(expectedProjectId, id, ref, expectedSha,
                "https://mock.invalid/projects/" + expectedProjectId + "/pipelines/" + id));
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

    private String projectId(String repoCloneUrl) {
        return projectIds.computeIfAbsent(repoCloneUrl, ignored -> String.valueOf(projectSequence.incrementAndGet()));
    }

    private void ensureDefaultBranches(Set<String> branchSet) {
        branchSet.add("main");
        branchSet.add("master");
    }
}
