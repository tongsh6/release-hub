package io.releasehub.bootstrap.e2e;

import io.releasehub.infrastructure.git.MockGitBranchAdapter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Utility class extending MockGitBranchAdapter with operation recording.
 *
 * <p>NOT registered as a Spring bean. Tests that need recording capabilities
 * create instances directly. This ensures {@code RealGitLabBranchAdapter}
 * activates when {@code releasehub.gitlab.real-adapter=true}.</p>
 *
 * <p>Every write operation is recorded with parameters and result.
 * Force-failure injection enables merge conflict scenario testing.</p>
 */
public class RecordingMockGitBranchAdapter extends MockGitBranchAdapter {

    private final List<BranchCreatedRecord> branchCreatedRecords = new CopyOnWriteArrayList<>();
    private final List<MergeRecord> mergeRecords = new CopyOnWriteArrayList<>();
    private final List<TagRecord> tagRecords = new CopyOnWriteArrayList<>();
    private final List<ArchiveRecord> archiveRecords = new CopyOnWriteArrayList<>();
    private final List<PipelineTriggerRecord> pipelineTriggerRecords = new CopyOnWriteArrayList<>();

    private volatile MergeResult forcedMergeResult = null;
    private volatile MergeabilityResult forcedMergeabilityResult = null;

    // ---- Configuration for error scenarios ----

    public void forceMergeResult(MergeResult result) {
        this.forcedMergeResult = result;
    }

    public void forceMergeabilityResult(MergeabilityResult result) {
        this.forcedMergeabilityResult = result;
    }

    public void resetForceOverrides() {
        this.forcedMergeResult = null;
        this.forcedMergeabilityResult = null;
    }

    public void clearRecords() {
        branchCreatedRecords.clear();
        mergeRecords.clear();
        tagRecords.clear();
        archiveRecords.clear();
        pipelineTriggerRecords.clear();
    }

    // ---- Override write operations to record calls ----

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        boolean result = super.createBranch(repoCloneUrl, token, branchName, fromBranch);
        branchCreatedRecords.add(new BranchCreatedRecord(repoCloneUrl, branchName, fromBranch, result));
        return result;
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        if (forcedMergeResult != null) {
            MergeResult forced = forcedMergeResult;
            mergeRecords.add(new MergeRecord(repoCloneUrl, sourceBranch, targetBranch, commitMessage, forced));
            return forced;
        }
        MergeResult result = super.mergeBranch(repoCloneUrl, token, sourceBranch, targetBranch, commitMessage);
        mergeRecords.add(new MergeRecord(repoCloneUrl, sourceBranch, targetBranch, commitMessage, result));
        return result;
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        if (forcedMergeabilityResult != null) {
            return forcedMergeabilityResult;
        }
        return super.checkMergeability(repoCloneUrl, token, sourceBranch, targetBranch);
    }

    @Override
    public boolean createTag(String repoCloneUrl, String token, String tagName, String ref, String message) {
        boolean result = super.createTag(repoCloneUrl, token, tagName, ref, message);
        tagRecords.add(new TagRecord(repoCloneUrl, tagName, ref, message, result));
        return result;
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        boolean result = super.archiveBranch(repoCloneUrl, token, branchName, reason);
        archiveRecords.add(new ArchiveRecord(repoCloneUrl, branchName, reason, result));
        return result;
    }

    @Override
    public String triggerPipeline(String repoCloneUrl, String token, String ref) {
        String pipelineId = super.triggerPipeline(repoCloneUrl, token, ref);
        pipelineTriggerRecords.add(new PipelineTriggerRecord(repoCloneUrl, ref, pipelineId));
        return pipelineId;
    }

    // ---- Query methods for test assertions ----

    public List<BranchCreatedRecord> getRecordedBranchCreations() {
        return Collections.unmodifiableList(branchCreatedRecords);
    }

    public List<MergeRecord> getRecordedMerges() {
        return Collections.unmodifiableList(mergeRecords);
    }

    public List<TagRecord> getRecordedTags() {
        return Collections.unmodifiableList(tagRecords);
    }

    public List<ArchiveRecord> getRecordedArchives() {
        return Collections.unmodifiableList(archiveRecords);
    }

    public List<PipelineTriggerRecord> getRecordedPipelineTriggers() {
        return Collections.unmodifiableList(pipelineTriggerRecords);
    }

    // ---- Record types ----

    public record BranchCreatedRecord(String repoUrl, String branchName, String fromBranch, boolean success) {}
    public record MergeRecord(String repoUrl, String sourceBranch, String targetBranch, String commitMessage, MergeResult result) {}
    public record TagRecord(String repoUrl, String tagName, String ref, String message, boolean success) {}
    public record ArchiveRecord(String repoUrl, String branchName, String reason, boolean success) {}
    public record PipelineTriggerRecord(String repoUrl, String ref, String pipelineId) {}
}
