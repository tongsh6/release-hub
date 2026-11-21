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

    public MergeResult merge(String source, String target, String message) {
        var sourceRead = port.readSource(source);
        var context = evidence(null, source, target, null, null);
        if (!found(sourceRead) || !validSha(sourceRead.evidence().sha())
                || sourceRead.evidence().projectId() == null) {
            return readFailure(sourceRead, "SOURCE_REVISION_UNCONFIRMED", context);
        }
        var revision = sourceRead.evidence();
        context = new MrEvidence(revision.projectId(), source, target, null, null, revision.sha(), null, null, null);
        var opened = port.find(source, target, State.OPENED);
        if (!validList(opened, revision.projectId(), source, target, State.OPENED)) {
            return readFailure(opened, "MR_LOOKUP_UNCONFIRMED", context);
        }
        if (opened.evidence().size() > 1) return MergeResult.unknown("MULTIPLE_OPEN_MRS").withEvidence(context);
        MergeRequest mr = opened.evidence().isEmpty() ? null : opened.evidence().get(0);
        if (mr != null) context = evidence(mr, source, target, MrDisposition.MR_REUSED, null);

        // 历史 merged 状态不能证明 target 未被重置；必须读取当前包含关系。
        var contained = port.contains(target, revision.sha());
        if (!found(contained)) return readFailure(contained, "TARGET_CONTAINMENT_UNCONFIRMED", context);
        if (Boolean.TRUE.equals(contained.evidence())) {
            var merged = port.find(source, target, State.MERGED);
            if (!validList(merged, revision.projectId(), source, target, State.MERGED)) {
                return readFailure(merged, "MERGED_MR_LOOKUP_UNCONFIRMED", context);
            }
            var previous = merged.evidence().stream()
                    .filter(candidate -> revision.sha().equals(candidate.sourceSha())).findFirst().orElse(null);
            boolean exactOpened = mr != null && revision.sha().equals(mr.sourceSha());
            return MergeResult.success().withEvidence(previous == null
                    ? new MrEvidence(revision.projectId(), source, target, exactOpened ? mr.id() : null,
                            exactOpened ? mr.url() : null, revision.sha(), null, MrDisposition.ALREADY_MERGED, null)
                    : evidence(previous, source, target, MrDisposition.ALREADY_MERGED, null));
        }

        MrDisposition disposition;
        if (mr == null) {
            var created = port.create(source, target, message);
            if (created.outcome() != GitWriteResult.Outcome.APPLIED) {
                return failedWrite(created, context);
            }
            mr = created.evidence();
            disposition = MrDisposition.CREATED;
        } else {
            disposition = MrDisposition.MR_REUSED;
            var observed = port.read(mr.id());
            if (!found(observed)) return readFailure(observed, "MR_READ_UNCONFIRMED", context);
            if (!Objects.equals(mr.id(), observed.evidence().id())) {
                return MergeResult.unknown("MR_IDENTITY_CHANGED").withEvidence(context);
            }
            mr = observed.evidence();
        }
        if (!matches(mr, revision.projectId(), source, target)) {
            return MergeResult.unknown("MR_IDENTITY_UNCONFIRMED").withEvidence(context);
        }
        mr = awaitReadiness(mr);
        context = evidence(mr, source, target, disposition, null);
        if (!revision.sha().equals(mr.sourceSha())) {
            return MergeResult.unknown("SOURCE_REVISION_CHANGED").withEvidence(context);
        }
        if (mr.state() != State.OPENED) {
            return MergeResult.unknown("MR_STATE_CHANGED").withEvidence(context);
        }
        if (mr.readiness() == Readiness.BLOCKED) {
            return MergeResult.blocked(mr.blockReason() == null ? MergeBlockReason.UNKNOWN : mr.blockReason(),
                    mr.detail()).withEvidence(context);
        }
        if (mr.readiness() != Readiness.MERGEABLE) {
            return MergeResult.unknown(mr.detail() == null ? "MERGE_READINESS_UNKNOWN" : mr.detail()).withEvidence(context);
        }

        var merged = port.merge(mr.id(), revision.sha(), message);
        if (merged.outcome() != GitWriteResult.Outcome.APPLIED) return failedWrite(merged, context);
        var confirmed = merged.evidence();
        if (!matches(confirmed, revision.projectId(), source, target)
                || !Objects.equals(mr.id(), confirmed.id()) || confirmed.state() != State.MERGED
                || !revision.sha().equals(confirmed.sourceSha())) {
            return MergeResult.unknown("MERGE_RESULT_UNCONFIRMED").withEvidence(context);
        }
        return MergeResult.success().withEvidence(evidence(confirmed, source, target, disposition, null));
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
        return mr != null && mr.id() != null && !mr.id().isBlank() && projectId != null
                && projectId.equals(mr.projectId()) && source.equals(mr.sourceBranch()) && target.equals(mr.targetBranch());
    }

    private static boolean found(GitReadResult<?> result) {
        return result != null && result.status() == GitReadResult.Status.FOUND && result.evidence() != null;
    }

    private static boolean validSha(String sha) { return sha != null && sha.matches("[0-9a-fA-F]{40,64}"); }

    private MrEvidence evidence(MergeRequest mr, String source, String target,
                                MrDisposition disposition, MergeBlockReason reason) {
        return mr == null ? new MrEvidence(null, source, target, null, null, null, null, disposition, reason)
                : new MrEvidence(mr.projectId(), source, target, mr.id(), mr.url(), mr.sourceSha(), mr.mergeSha(), disposition, reason);
    }
}
