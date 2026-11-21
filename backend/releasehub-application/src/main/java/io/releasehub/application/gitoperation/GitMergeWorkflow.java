package io.releasehub.application.gitoperation;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import io.releasehub.application.port.out.GitBranchPort.MergeResult;
import io.releasehub.application.port.out.GitBranchPort.MrDisposition;
import io.releasehub.application.port.out.GitBranchPort.MrEvidence;
import java.util.List;
import java.util.Objects;
import static io.releasehub.application.gitoperation.GitMergeRequestPort.*;

/** 合并决策留在应用层。复用 MR，只读轮询可重试，任何写入都不隐式重发或关闭。 */
public final class GitMergeWorkflow {
    private final GitMergeRequestPort port;
    private final int maxPolls;
    private final Runnable pollWait;

    public GitMergeWorkflow(GitMergeRequestPort port, int maxPolls, Runnable pollWait) {
        this.port = port;
        this.maxPolls = maxPolls;
        this.pollWait = pollWait;
    }

    /** Cleanup keeps the same policy by composing the two actions without creating another merge path. */
    public MergeResult merge(String source, String target, String message) {
        var ensured = ensureMergeRequest(source, target, message);
        if (ensured.outcome() == GitWriteResult.Outcome.APPLIED) return tryMergeRequest(ensured.evidence(), message);
        if (ensured.outcome() == GitWriteResult.Outcome.UNKNOWN) {
            return MergeResult.unknown(ensured.reason()).withEvidence(ensured.evidence());
        }
        var reason = ensured.evidence() == null ? null : ensured.evidence().blockReason();
        return MergeResult.blocked(reason == null ? MergeBlockReason.UNKNOWN : reason, ensured.reason())
                .withEvidence(ensured.evidence());
    }

    /** APPLIED confirms this ensure action, including read-only reuse; it does not mean the MR was merged. */
    public GitWriteResult<MrEvidence> ensureMergeRequest(String source, String target, String message) {
        var sourceRead = port.readSource(source);
        var context = evidence(null, source, target, null, null);
        if (!found(sourceRead) || !validSha(sourceRead.evidence().sha())
                || !numericId(sourceRead.evidence().projectId())) {
            return ensureResult(readFailure(sourceRead, "SOURCE_REVISION_UNCONFIRMED", context));
        }
        var revision = sourceRead.evidence();
        context = new MrEvidence(revision.projectId(), source, target, null, null, revision.sha(), null, null, null);
        var opened = port.find(source, target, State.OPENED);
        if (!validList(opened, revision.projectId(), source, target, State.OPENED)) {
            return ensureResult(readFailure(opened, "MR_LOOKUP_UNCONFIRMED", context));
        }
        if (opened.evidence().size() > 1) {
            return ensureResult(MergeResult.unknown("MULTIPLE_OPEN_MRS").withEvidence(context));
        }
        MergeRequest mr = opened.evidence().isEmpty() ? null : opened.evidence().get(0);
        if (mr != null) context = evidence(mr, source, target, MrDisposition.MR_REUSED, null);

        var contained = port.contains(target, revision.sha());
        if (!found(contained)) return ensureResult(readFailure(contained, "TARGET_CONTAINMENT_UNCONFIRMED", context));
        if (Boolean.TRUE.equals(contained.evidence())) {
            var merged = port.find(source, target, State.MERGED);
            if (!validList(merged, revision.projectId(), source, target, State.MERGED)) {
                return ensureResult(readFailure(merged, "MERGED_MR_LOOKUP_UNCONFIRMED", context));
            }
            var previous = merged.evidence().stream()
                    .filter(candidate -> revision.sha().equals(candidate.sourceSha())).findFirst().orElse(null);
            boolean exactOpened = mr != null && revision.sha().equals(mr.sourceSha());
            var checkpoint = previous == null
                    ? new MrEvidence(revision.projectId(), source, target, exactOpened ? mr.id() : null,
                            exactOpened ? mr.url() : null, revision.sha(), null, MrDisposition.ALREADY_MERGED, null)
                    : evidence(previous, source, target, MrDisposition.ALREADY_MERGED, null);
            return isValidCheckpoint(checkpoint) ? GitWriteResult.applied(checkpoint)
                    : ensureResult(MergeResult.unknown("MR_CHECKPOINT_UNCONFIRMED").withEvidence(checkpoint));
        }

        MrDisposition disposition;
        if (mr == null) {
            var created = port.create(source, target, revision.sha(), message);
            if (created == null) return ensureResult(MergeResult.unknown("CREATE_RESULT_UNCONFIRMED").withEvidence(context));
            if (created.outcome() != GitWriteResult.Outcome.APPLIED) return ensureResult(failedWrite(created, context));
            mr = created.evidence();
            disposition = MrDisposition.CREATED;
        } else {
            disposition = MrDisposition.MR_REUSED;
            var observed = port.read(mr.id());
            if (!found(observed)) return ensureResult(readFailure(observed, "MR_READ_UNCONFIRMED", context));
            if (!Objects.equals(mr.id(), observed.evidence().id())) {
                return ensureResult(MergeResult.unknown("MR_IDENTITY_CHANGED").withEvidence(context));
            }
            mr = observed.evidence();
        }
        if (!matches(mr, revision.projectId(), source, target)) {
            return ensureResult(MergeResult.unknown("MR_IDENTITY_UNCONFIRMED").withEvidence(context));
        }
        context = disposition == MrDisposition.CREATED
                ? new MrEvidence(mr.projectId(), source, target, mr.id(), mr.url(), mr.sourceSha(), null, disposition, null)
                : evidence(mr, source, target, disposition, null);
        boolean sameRevision = disposition == MrDisposition.CREATED
                ? revision.sha().equalsIgnoreCase(mr.sourceSha()) : revision.sha().equals(mr.sourceSha());
        if (!sameRevision) {
            return ensureResult(MergeResult.unknown("SOURCE_REVISION_CHANGED").withEvidence(context));
        }
        if (mr.state() != State.OPENED || !isValidCheckpoint(context)) {
            return ensureResult(MergeResult.unknown("MR_STATE_UNCONFIRMED").withEvidence(context));
        }
        // Readiness is checked by TRY_MERGE after this checkpoint has been durably committed.
        return GitWriteResult.applied(context);
    }

    public MergeResult tryMergeRequest(MrEvidence checkpoint, String message) {
        if (!isValidCheckpoint(checkpoint)) return MergeResult.unknown("MR_CHECKPOINT_INVALID").withEvidence(checkpoint);
        var contained = port.contains(checkpoint.targetBranch(), checkpoint.sourceSha());
        if (!found(contained)) return readFailure(contained, "TARGET_CONTAINMENT_UNCONFIRMED", checkpoint);
        if (Boolean.TRUE.equals(contained.evidence())) return alreadyMerged(checkpoint);
        if (checkpoint.disposition() == MrDisposition.ALREADY_MERGED) {
            return MergeResult.unknown("TARGET_CONTAINMENT_CHANGED").withEvidence(checkpoint);
        }

        var observed = port.read(checkpoint.iid());
        if (!found(observed)) return readFailure(observed, "MR_READ_UNCONFIRMED", checkpoint);
        var mr = observed.evidence();
        if (!matchesCheckpoint(mr, checkpoint)) return MergeResult.unknown("MR_CHECKPOINT_CHANGED").withEvidence(checkpoint);
        if (mr.state() == State.MERGED) {
            var current = port.contains(checkpoint.targetBranch(), checkpoint.sourceSha());
            if (!found(current)) return readFailure(current, "TARGET_CONTAINMENT_UNCONFIRMED", checkpoint);
            if (!Boolean.TRUE.equals(current.evidence())) {
                return MergeResult.unknown("TARGET_CONTAINMENT_CHANGED").withEvidence(checkpoint);
            }
            return alreadyMerged(evidence(mr, checkpoint.sourceBranch(), checkpoint.targetBranch(),
                    MrDisposition.ALREADY_MERGED, null));
        }
        if (mr.state() != State.OPENED) return MergeResult.unknown("MR_STATE_CHANGED").withEvidence(checkpoint);
        mr = awaitReadiness(mr);
        if (!matchesCheckpoint(mr, checkpoint) || mr.state() != State.OPENED) {
            return MergeResult.unknown("MR_CHECKPOINT_CHANGED").withEvidence(checkpoint);
        }
        var context = evidence(mr, checkpoint.sourceBranch(), checkpoint.targetBranch(), checkpoint.disposition(), null);
        if (mr.readiness() == Readiness.BLOCKED) {
            return MergeResult.blocked(mr.blockReason() == null ? MergeBlockReason.UNKNOWN : mr.blockReason(),
                    mr.detail()).withEvidence(context);
        }
        if (mr.readiness() != Readiness.MERGEABLE) {
            return MergeResult.unknown(mr.detail() == null ? "MERGE_READINESS_UNKNOWN" : mr.detail()).withEvidence(context);
        }
        var merged = port.merge(checkpoint.iid(), checkpoint.sourceSha(), message);
        if (merged == null) return MergeResult.unknown("MERGE_RESULT_UNCONFIRMED").withEvidence(context);
        if (merged.outcome() != GitWriteResult.Outcome.APPLIED) return failedWrite(merged, context);
        var confirmed = merged.evidence();
        if (!matchesCheckpoint(confirmed, checkpoint) || confirmed.state() != State.MERGED) {
            return MergeResult.unknown("MERGE_RESULT_UNCONFIRMED").withEvidence(context);
        }
        return MergeResult.success().withEvidence(evidence(confirmed, checkpoint.sourceBranch(),
                checkpoint.targetBranch(), checkpoint.disposition(), null));
    }

    private MergeResult alreadyMerged(MrEvidence checkpoint) {
        String mergeSha = checkpoint.mergeSha();
        if (mergeSha != null && !mergeSha.equals(checkpoint.sourceSha())) {
            var contained = port.contains(checkpoint.targetBranch(), mergeSha);
            if (!found(contained)) return readFailure(contained, "MERGE_COMMIT_CONTAINMENT_UNCONFIRMED", checkpoint);
            if (!Boolean.TRUE.equals(contained.evidence())) mergeSha = null;
        }
        return MergeResult.success().withEvidence(new MrEvidence(checkpoint.projectId(), checkpoint.sourceBranch(),
                checkpoint.targetBranch(), checkpoint.iid(), checkpoint.url(), checkpoint.sourceSha(), mergeSha,
                MrDisposition.ALREADY_MERGED, null));
    }

    private GitWriteResult<MrEvidence> ensureResult(MergeResult result) {
        return new GitWriteResult<>(result.status() == io.releasehub.domain.run.MergeStatus.UNKNOWN
                ? GitWriteResult.Outcome.UNKNOWN : GitWriteResult.Outcome.REJECTED, result.mrEvidence(), result.detail());
    }

    /** Shape validation only; TRY_MERGE still verifies the recorded identity and current remote state. */
    public static boolean isValidCheckpoint(MrEvidence checkpoint) {
        if (checkpoint == null || !numericId(checkpoint.projectId()) || !validSha(checkpoint.sourceSha())
                || !nonBlank(checkpoint.sourceBranch()) || !nonBlank(checkpoint.targetBranch())
                || checkpoint.disposition() == null
                || checkpoint.mergeSha() != null && !validSha(checkpoint.mergeSha())) return false;
        boolean completeMr = numericId(checkpoint.iid()) && validUrl(checkpoint.url());
        return completeMr || checkpoint.disposition() == MrDisposition.ALREADY_MERGED
                && checkpoint.iid() == null && checkpoint.url() == null;
    }

    private boolean matchesCheckpoint(MergeRequest mr, MrEvidence checkpoint) {
        return matches(mr, checkpoint.projectId(), checkpoint.sourceBranch(), checkpoint.targetBranch())
                && checkpoint.iid().equals(mr.id()) && checkpoint.sourceSha().equalsIgnoreCase(mr.sourceSha());
    }

    /** 检查只读：没有现存 MR 时无法由 MR API 确认可合并性。 */
    public GitBranchPort.MergeabilityResult probe(String source, String target) {
        var sourceRead = port.readSource(source);
        if (!found(sourceRead)) return probeFailure(sourceRead, "SOURCE_REVISION_UNCONFIRMED");
        var revision = sourceRead.evidence();
        var opened = port.find(source, target, State.OPENED);
        if (!validList(opened, revision.projectId(), source, target, State.OPENED)) {
            return probeFailure(opened, "MR_LOOKUP_UNCONFIRMED");
        }
        if (opened.evidence().size() != 1) return GitBranchPort.MergeabilityResult.error("NO_UNIQUE_OPEN_MR");
        var candidate = opened.evidence().get(0);
        var observed = port.read(candidate.id());
        if (!found(observed)) return probeFailure(observed, "MR_READ_UNCONFIRMED");
        var mr = observed.evidence();
        if (!Objects.equals(candidate.id(), mr.id()) || !matches(mr, revision.projectId(), source, target)) {
            return GitBranchPort.MergeabilityResult.error("MR_IDENTITY_CHANGED");
        }
        mr = awaitReadiness(mr);
        if (!validSha(revision.sha()) || !revision.sha().equals(mr.sourceSha()) || mr.state() != State.OPENED) {
            return GitBranchPort.MergeabilityResult.error("MR_REVISION_UNCONFIRMED");
        }
        if (mr.readiness() == Readiness.MERGEABLE) return GitBranchPort.MergeabilityResult.mergeable();
        if (mr.blockReason() == MergeBlockReason.CONFLICT) return GitBranchPort.MergeabilityResult.conflict(mr.detail());
        if (mr.blockReason() == MergeBlockReason.PERMISSION_DENIED) return GitBranchPort.MergeabilityResult.permissionDenied(mr.detail());
        return GitBranchPort.MergeabilityResult.error(mr.detail() == null ? "MERGE_READINESS_UNKNOWN" : mr.detail());
    }

    private MergeRequest awaitReadiness(MergeRequest initial) {
        var mr = initial;
        for (int i = 0; i < maxPolls && mr.readiness() == Readiness.PENDING; i++) {
            pollWait.run();
            if (Thread.currentThread().isInterrupted()) break;
            var observed = port.read(mr.id());
            if (!found(observed)) {
                return mr.withReadiness(Readiness.UNKNOWN, MergeBlockReason.UNKNOWN, "MR_READ_" + observed.status());
            }
            var next = observed.evidence();
            if (!Objects.equals(mr.id(), next.id())
                    || !matches(next, initial.projectId(), initial.sourceBranch(), initial.targetBranch())) {
                return mr.withReadiness(Readiness.UNKNOWN, MergeBlockReason.UNKNOWN, "MR_IDENTITY_CHANGED");
            }
            mr = next;
        }
        return mr;
    }

    private MergeResult failedWrite(GitWriteResult<MergeRequest> result, MrEvidence context) {
        var observed = result.evidence();
        if (observed != null && observed.id() != null
                && (observed.projectId() == null || Objects.equals(context.projectId(), observed.projectId()))) {
            context = new MrEvidence(context.projectId(), context.sourceBranch(), context.targetBranch(),
                    observed.id(), observed.url(), context.sourceSha(), observed.mergeSha(),
                    context.disposition(), context.blockReason());
        }
        if (result.outcome() == GitWriteResult.Outcome.UNKNOWN || "NO_COMMITS".equals(result.reason())) {
            return MergeResult.unknown(result.reason()).withEvidence(context);
        }
        MergeBlockReason reason;
        try { reason = result.reason() == null ? MergeBlockReason.UNKNOWN : MergeBlockReason.valueOf(result.reason()); }
        catch (IllegalArgumentException e) {
            reason = "HTTP_401".equals(result.reason()) || "HTTP_403".equals(result.reason())
                    ? MergeBlockReason.PERMISSION_DENIED : MergeBlockReason.UNKNOWN;
        }
        return MergeResult.blocked(reason, result.reason()).withEvidence(context);
    }

    private MergeResult readFailure(GitReadResult<?> read, String detail, MrEvidence context) {
        if (read != null && read.status() == GitReadResult.Status.FORBIDDEN) {
            return MergeResult.blocked(MergeBlockReason.PERMISSION_DENIED, detail).withEvidence(context);
        }
        return MergeResult.unknown(detail).withEvidence(context);
    }

    private GitBranchPort.MergeabilityResult probeFailure(GitReadResult<?> read, String detail) {
        return read != null && read.status() == GitReadResult.Status.FORBIDDEN
                ? GitBranchPort.MergeabilityResult.permissionDenied(detail)
                : GitBranchPort.MergeabilityResult.error(detail);
    }

    private boolean validList(GitReadResult<List<MergeRequest>> read, String projectId,
                              String source, String target, State state) {
        return found(read) && read.evidence().stream()
                .allMatch(mr -> matches(mr, projectId, source, target) && mr.state() == state);
    }

    private boolean matches(MergeRequest mr, String projectId, String source, String target) {
        return mr != null && numericId(mr.id()) && validUrl(mr.url()) && validSha(mr.sourceSha())
                && numericId(projectId) && projectId.equals(mr.projectId())
                && source.equals(mr.sourceBranch()) && target.equals(mr.targetBranch());
    }

    private static boolean found(GitReadResult<?> result) {
        return result != null && result.status() == GitReadResult.Status.FOUND && result.evidence() != null;
    }

    private static boolean validSha(String sha) { return sha != null && sha.matches("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})"); }
    private static boolean numericId(String value) { return value != null && value.matches("[1-9][0-9]*"); }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static boolean validUrl(String value) {
        if (!nonBlank(value)) return false;
        try {
            var uri = java.net.URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && nonBlank(uri.getPath()) && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private MrEvidence evidence(MergeRequest mr, String source, String target,
                                MrDisposition disposition, MergeBlockReason reason) {
        return mr == null ? new MrEvidence(null, source, target, null, null, null, null, disposition, reason)
                : new MrEvidence(mr.projectId(), source, target, mr.id(), mr.url(), mr.sourceSha(), mr.mergeSha(), disposition, reason);
    }
}
