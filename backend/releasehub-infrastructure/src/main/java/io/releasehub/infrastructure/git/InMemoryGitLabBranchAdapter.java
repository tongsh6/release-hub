package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;

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
    private final Map<String, Map<String, TagEvidence>> tags = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong pipelineIds = new java.util.concurrent.atomic.AtomicLong();

    // Test-only repositories have a single synthetic commit; no remote Git state is implied.
    private static final String FIXTURE_COMMIT_SHA = "1".repeat(40);
    private final Map<String, String> projectIds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong projectSequence = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong mergeRequestIds = new java.util.concurrent.atomic.AtomicLong();
    private final Map<String, Map<List<String>, MrEvidence>> mergeRequests = new ConcurrentHashMap<>();
    private final Set<String> completedMerges = ConcurrentHashMap.newKeySet();

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
        var ensured = ensureMergeRequest(repoCloneUrl, token, sourceBranch, targetBranch, commitMessage);
        if (ensured.outcome() != GitWriteResult.Outcome.APPLIED) return ensured.outcome() == GitWriteResult.Outcome.UNKNOWN
                ? MergeResult.unknown(ensured.reason()).withEvidence(ensured.evidence()) : MergeResult.failed(ensured.reason());
        return tryMergeRequest(repoCloneUrl, token, ensured.evidence(), commitMessage);
    }

    @Override
    public GitWriteResult<MrEvidence> ensureMergeRequest(String repoCloneUrl, String token, String sourceBranch,
                                                        String targetBranch, String message) {
        if (!getBranchStatus(repoCloneUrl, token, sourceBranch).exists()) return GitWriteResult.rejected("SOURCE_BRANCH_NOT_FOUND");
        if (!getBranchStatus(repoCloneUrl, token, targetBranch).exists()) return GitWriteResult.rejected("TARGET_BRANCH_NOT_FOUND");
        String projectId = projectId(repoCloneUrl);
        String iid = String.valueOf(mergeRequestIds.incrementAndGet());
        var created = new MrEvidence(projectId, sourceBranch, targetBranch, iid,
                "https://in-memory.invalid/projects/" + projectId + "/merge_requests/" + iid,
                FIXTURE_COMMIT_SHA, null, MrDisposition.CREATED, null);
        var previous = mergeRequests.computeIfAbsent(repoCloneUrl, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(List.of(sourceBranch, targetBranch), created);
        if (previous == null) return GitWriteResult.applied(created);
        boolean merged = completedMerges.contains(previous.iid());
        return GitWriteResult.applied(new MrEvidence(previous.projectId(), sourceBranch, targetBranch,
                previous.iid(), previous.url(), previous.sourceSha(), merged ? FIXTURE_COMMIT_SHA : null,
                merged ? MrDisposition.ALREADY_MERGED : MrDisposition.MR_REUSED, null));
    }

    @Override
    public MergeResult tryMergeRequest(String repoCloneUrl, String token, MrEvidence checkpoint, String message) {
        if (!io.releasehub.application.gitoperation.GitMergeWorkflow.isValidCheckpoint(checkpoint)
                || !projectId(repoCloneUrl).equals(checkpoint.projectId())) return MergeResult.unknown("MR_CHECKPOINT_INVALID");
        var stored = mergeRequests.getOrDefault(repoCloneUrl, Map.of())
                .get(List.of(checkpoint.sourceBranch(), checkpoint.targetBranch()));
        if (stored == null || !stored.iid().equals(checkpoint.iid()) || !stored.url().equals(checkpoint.url())
                || !stored.sourceSha().equals(checkpoint.sourceSha())
                || !getBranchStatus(repoCloneUrl, token, checkpoint.sourceBranch()).exists()
                || !getBranchStatus(repoCloneUrl, token, checkpoint.targetBranch()).exists()) {
            return MergeResult.unknown("MR_CHECKPOINT_CHANGED").withEvidence(checkpoint);
        }
        if (checkpoint.disposition() == MrDisposition.ALREADY_MERGED && !completedMerges.contains(stored.iid())) {
            return MergeResult.unknown("MR_STATE_CHANGED").withEvidence(checkpoint);
        }
        boolean firstMerge = completedMerges.add(stored.iid());
        return MergeResult.success().withEvidence(new MrEvidence(stored.projectId(), stored.sourceBranch(),
                stored.targetBranch(), stored.iid(), stored.url(), stored.sourceSha(), FIXTURE_COMMIT_SHA,
                firstMerge ? checkpoint.disposition() : MrDisposition.ALREADY_MERGED, null));
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
                "https://in-memory.invalid/projects/" + expectedProjectId + "/pipelines/" + id));
    }

    @Override
    public List<String> listBranches(String repoCloneUrl, String token, String prefix) {
        String branchPrefix = prefix == null ? "" : prefix;
        Set<String> branchSet = branches.getOrDefault(repoCloneUrl, Set.of());
        return branchSet.stream()
                .filter(b -> branchPrefix.isBlank() || b.startsWith(branchPrefix))
                .toList();
    }

    private String projectId(String repoCloneUrl) {
        return projectIds.computeIfAbsent(repoCloneUrl, ignored -> String.valueOf(projectSequence.incrementAndGet()));
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        Set<String> branchSet = branches.get(repoCloneUrl);
        boolean exists = branchSet != null && branchSet.contains(branchName);
        if (!exists && ("main".equals(branchName) || "master".equals(branchName))) {
            exists = true;
        }
        return exists ? BranchStatus.present(projectId(repoCloneUrl), FIXTURE_COMMIT_SHA) : BranchStatus.missing(projectId(repoCloneUrl));
    }
}
