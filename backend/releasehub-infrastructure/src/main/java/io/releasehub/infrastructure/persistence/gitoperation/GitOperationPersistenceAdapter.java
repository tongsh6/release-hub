package io.releasehub.infrastructure.persistence.gitoperation;

import com.fasterxml.jackson.databind.JsonNode;
import io.releasehub.infrastructure.git.GitLabGitBranchAdapter;
import io.releasehub.infrastructure.git.GitLabMergeRequestAdapter;
import io.releasehub.application.port.out.GitBranchPort.MrEvidence;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import io.releasehub.application.port.out.GitBranchPort.MrDisposition;
import io.releasehub.domain.run.ActionType;
import io.releasehub.application.gitoperation.*;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.infrastructure.persistence.run.RunJpaEntity;
import io.releasehub.infrastructure.persistence.run.RunItemJpaEntity;
import io.releasehub.infrastructure.persistence.run.RunTaskJpaEntity;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

/** HTTP 意图、发送与响应独立提交；本地任务确认及未发送收口加入任务结果事务。 */
@Repository @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GitOperationPersistenceAdapter implements GitOperationJournalPort {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final io.releasehub.application.repo.CodeRepositoryPort repos;
    private final io.releasehub.application.settings.SettingsPort settings;

    @Override public Operation begin(Command c) {
        if ("CREATE_MR".equals(c.action())
                && (!validCreateMrIdentity(c.provider(), c.baseUrl(), c.projectId(), c.remoteKey())
                || c.sessionId() == null || c.sessionId().isBlank()
                || !completeCreateMrRequest(c.operationId(), objectJson(c.targetJson()), c.remoteKey(), c.requestHash()))) throw invalid();
        validateTaskCommand(c);
        if(!GitOperationJournalPort.remoteKey(c.provider(),c.baseUrl(),c.projectId()).equals(c.remoteKey()))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        jdbc.query("select pg_advisory_xact_lock_shared(hashtextextended('git-mapping-admission', 2))",(RowCallbackHandler) r->{});
        lock("repo:"+c.repoId());
        lock(c.remoteKey());
        if(!c.provider().equals("GITLAB"))throw BusinessException.of(ErrorCode.GIT_PROVIDER_UNSUPPORTED);
        String configured=settings.getGitLab().map(s->GitOperationJournalPort.configuredBaseUrl(s.baseUrl())).orElse("");
        if(!configured.equals(c.baseUrl()))throw stale();
        var repo=repos.findById(io.releasehub.domain.repo.RepoId.of(c.repoId())).orElseThrow(this::blocked);
        if(!GitOperationJournalPort.mappingHash(repo.getGitProvider().name(),repo.getCloneUrl()).equals(c.repositoryMapping()))throw stale();
        long changedIdentity=em.createQuery("select count(o) from GitOperationEntity o where o.repoId=:repo and o.remoteKey<>:remote and exists (select g.remoteKey from RepoExecutionGuardEntity g where g.remoteKey=o.remoteKey and g.sessionId=o.sessionId)",Long.class)
                .setParameter("repo",c.repoId()).setParameter("remote",c.remoteKey()).getSingleResult();
        if(changedIdentity>0)throw blocked();
        if (c.taskFence() != null && ("MERGE_MR".equals(c.action()) || "CREATE_BRANCH".equals(c.action()) || "CREATE_MR".equals(c.action()))) {
            var originals = taskOperations(c.taskFence().taskId());
            if (!originals.isEmpty()) {
                if (originals.size() != 1) throw blocked();
                var original = originals.getFirst();
                if (!c.remoteKey().equals(original.getRemoteKey())) throw stale();
                em.refresh(original, LockModeType.PESSIMISTIC_WRITE);
                var task = em.find(RunTaskJpaEntity.class, c.taskFence().taskId());
                var item = em.find(RunItemJpaEntity.class, task.getTargetId());
                if (!validUnsentOperation(task, item, original)) throw blocked();
                if (!Objects.equals(c.requestHash(), original.getRequestHash())
                        || !Objects.equals(objectJson(c.targetJson()), objectJson(original.getTargetJson()))) throw stale();
                return view(original);
            }
        }
        var existing = em.find(GitOperationEntity.class, c.operationId());
        if (existing != null) {
            boolean createMr = "CREATE_MR".equals(c.action());
            if (createMr) {
                if (!Objects.equals(c.remoteKey(), existing.getRemoteKey())) throw stale();
                em.refresh(existing, LockModeType.PESSIMISTIC_WRITE);
            }
            if (!existing.getRequestHash().equals(c.requestHash()) || !existing.getOperator().equals(c.operator())
                    || !Objects.equals(existing.getRunId(), c.runId()) || !existing.getSessionId().equals(c.sessionId())
                    || !Objects.equals(existing.getTaskId(), c.taskFence() == null ? null : c.taskFence().taskId())
                    || !Objects.equals(existing.getClaimToken(), c.taskFence() == null ? null : c.taskFence().claimToken())) throw stale();
            if (createMr && (!Objects.equals(c.repoId(), existing.getRepoId())
                    || !Objects.equals(c.remoteKey(), existing.getRemoteKey()) || !Objects.equals(c.action(), existing.getAction())
                    || !Objects.equals(c.provider(), existing.getProvider()) || !Objects.equals(c.baseUrl(), existing.getBaseUrl())
                    || !Objects.equals(c.projectId(), existing.getProjectId())
                    || !Objects.equals(objectJson(c.targetJson()), objectJson(existing.getTargetJson()))
                    || !validCreateMrIntent(existing, ownGuard(existing)))) throw stale();
            return view(existing);
        }
        if (c.taskFence() != null && em.createQuery("select count(o) from GitOperationEntity o where o.taskId=:task", Long.class)
                .setParameter("task", c.taskFence().taskId()).getSingleResult() > 0) throw blocked();
        var guard = em.find(RepoExecutionGuardEntity.class, c.remoteKey(), LockModeType.PESSIMISTIC_WRITE);
        if (guard == null) {
            guard = new RepoExecutionGuardEntity(); guard.setRemoteKey(c.remoteKey()); guard.setGeneration(0); guard.setUpdatedAt(now());
            em.persist(guard);
        }
        if (guard.getSessionId() != null && (!guard.getSessionId().equals(c.sessionId()) || guard.getReason() != null)) {
            throw blocked();
        }
        if (guard.getSessionId() == null) guard.setGeneration(guard.getGeneration() + 1);
        var op = new GitOperationEntity();
        op.setId(c.operationId()); op.setAttemptId(UUID.randomUUID().toString());
        op.setRepoId(c.repoId()); op.setRemoteKey(c.remoteKey()); op.setProvider(c.provider());
        op.setBaseUrl(c.baseUrl()); op.setProjectId(c.projectId()); op.setAction(c.action());
        op.setTargetJson(c.targetJson()); op.setRequestHash(c.requestHash()); op.setRunId(c.runId());
        op.setOperator(c.operator()); op.setSessionId(c.sessionId()); op.setGeneration(guard.getGeneration());
        if (c.taskFence() != null) { op.setTaskId(c.taskFence().taskId()); op.setClaimToken(c.taskFence().claimToken()); }
        op.setState("INTENT_RECORDED"); op.setCreatedAt(now()); op.setUpdatedAt(now()); em.persist(op);
        guard.setSessionId(c.sessionId()); guard.setOperationId(op.getId());
        guard.setReason("INTENT_PENDING"); guard.setUpdatedAt(now());
        event(op, 0, "INTENT_RECORDED", "{}", null);
        em.flush(); return view(op);
    }

    @Override public void sending(String id, RunPort.TaskFence fence) {
        var op = operationForUpdate(id, fence); var guard = ownGuard(op);
        if ("CREATE_MR".equals(op.getAction())) {
            if (!validCreateMrIntent(op, guard)) throw stale();
        } else if (!"INTENT_RECORDED".equals(op.getState()) || !onlyIntentEvent(op)
                || !"INTENT_PENDING".equals(guard.getReason())) throw stale();
        if (op.getTaskId() != null && "CREATE_BRANCH".equals(op.getAction())) {
            var task = em.find(RunTaskJpaEntity.class, op.getTaskId());
            var item = em.find(RunItemJpaEntity.class, task.getTargetId());
            if (!validUnsentOperation(task, item, op)) throw stale();
        }
        String sender = fence == null ? "{}" : JSON.createObjectNode()
                .put("taskId", fence.taskId()).put("claimToken", fence.claimToken()).toString();
        op.setState("SEND_STARTED"); op.setUpdatedAt(now());
        guard.setReason("REQUEST_IN_FLIGHT"); guard.setUpdatedAt(now());
        event(op, 1, "SEND_STARTED", sender, null); em.flush();
    }

    @Override public void finish(String id, RunPort.TaskFence fence,
                                 GitWriteResult.Outcome outcome, String evidence, String reason) {
        if (outcome == null) throw invalid();
        var op = operationForUpdate(id, fence); var guard = ownGuard(op);
        if (!"SEND_STARTED".equals(op.getState())
                || op.getTaskId() != null && ("MERGE_MR".equals(op.getAction()) || "CREATE_BRANCH".equals(op.getAction()) || "CREATE_MR".equals(op.getAction()))
                    && !sentBy(op, fence)) throw stale();
        op.setState(outcome.name()); op.setReason(reason); op.setUpdatedAt(now());
        guard.setReason(outcome == GitWriteResult.Outcome.UNKNOWN ? "REMOTE_RESULT_UNKNOWN" : null);
        guard.setUpdatedAt(now()); event(op, 2, outcome.name(), evidence, reason); em.flush();
    }

    @Override public void completeSession(String session, boolean committed) {
        if (em.createQuery("select count(o) from GitOperationEntity o where o.sessionId=:session and o.taskId is not null", Long.class)
                .setParameter("session", session).getSingleResult() > 0) throw stale();
        var guards = em.createQuery("select g from RepoExecutionGuardEntity g where g.sessionId=:session order by g.remoteKey", RepoExecutionGuardEntity.class)
                .setParameter("session", session).getResultList();
        for (var g : guards) {
            lock(g.getRemoteKey()); em.refresh(g, LockModeType.PESSIMISTIC_WRITE);
            if (!session.equals(g.getSessionId())) continue;
            var ops = em.createQuery("select o from GitOperationEntity o where o.sessionId=:session and o.remoteKey=:remote", GitOperationEntity.class)
                    .setParameter("session", session).setParameter("remote", g.getRemoteKey()).getResultList();
            boolean pending = ops.stream().anyMatch(o -> List.of("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN", "NEEDS_REVIEW").contains(o.getState()));
            boolean externalEffect = ops.stream().anyMatch(o -> "APPLIED".equals(o.getState()));
            if (pending || !committed && externalEffect) {
                g.setReason(committed ? "REMOTE_RESULT_UNKNOWN" : "LOCAL_RESULT_UNCONFIRMED");
                if (!committed) for (var op : ops) {
                    if ("APPLIED".equals(op.getState())) {
                        op.setState("NEEDS_REVIEW"); op.setReason("LOCAL_RESULT_UNCONFIRMED"); op.setUpdatedAt(now());
                        event(op, 3, "LOCAL_RESULT_UNCONFIRMED", "{}", "LOCAL_RESULT_UNCONFIRMED");
                    }
                }
            } else { g.setSessionId(null); g.setOperationId(null); g.setReason(null); }
            g.setUpdatedAt(now());
        }
        em.flush();
    }


    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Operation> prepareUnsentTaskClaim(RunPort.TaskClaim claim) {
        if (claim == null || (claim.actionType() != ActionType.TRY_MERGE
                && claim.actionType() != ActionType.ENSURE_FEATURE
                && claim.actionType() != ActionType.ENSURE_RELEASE
                && claim.actionType() != ActionType.ENSURE_MR)) return Optional.empty();
        var task = lockTask(claim.fence(), true);
        var run = em.find(RunJpaEntity.class, task.getRunId());
        var item = em.find(RunItemJpaEntity.class, task.getTargetId());
        if (item == null || !task.getRunId().equals(claim.runId()) || !item.getId().equals(claim.itemId())
                || !task.getTaskType().equals(claim.actionType().name())
                || !item.getRepoId().equals(claim.repo().value()) || !run.getOperator().equals(claim.operator())) return Optional.empty();
        var operations = taskOperations(task.getId());
        if (operations.size() != 1) return Optional.empty();
        var op = operations.getFirst();
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        if (!validUnsentOperation(task, item, op)) return Optional.empty();
        // Increment immediately in the caller's physical transaction, before its final Task CAS and clear().
        em.lock(op, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        em.flush();
        return Optional.of(view(op));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> confirmedAppliedBranchProject(RunPort.TaskClaim claim) {
        if (claim == null || claim.actionType() != ActionType.ENSURE_FEATURE
                && claim.actionType() != ActionType.ENSURE_RELEASE) return Optional.empty();
        var operation = confirmedAppliedTaskOperation(claim, "CREATE_BRANCH");
        if (operation.isEmpty()) return Optional.empty();
        var op = operation.get();
        var metadata = em.find(RunItemJpaEntity.class, claim.itemId()).getMetadata();
        JsonNode target = objectJson(op.getTargetJson());
        String branch = metadata.get(claim.actionType() == ActionType.ENSURE_FEATURE
                ? "plan.featureBranch" : "plan.releaseBranch");
        if (target == null || !matches(target, "branch", branch)
                || !matches(target, "sourceRef", metadata.get("plan.defaultBranch"))
                || !target.path("ref").isTextual() || !validSha(target.path("ref").asText())) return Optional.empty();
        JsonNode evidence = confirmedAppliedResponse(op);
        if (evidence == null) return Optional.empty();
        String sha = GitLabGitBranchAdapter.createdBranchSha(evidence.path("httpStatus").intValue(), evidence, branch);
        if (sha == null || !target.path("ref").asText().equalsIgnoreCase(sha)) return Optional.empty();
        return Optional.of(op.getProjectId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MrEvidence> confirmedAppliedCreatedMr(RunPort.TaskClaim claim) {
        if (claim == null || claim.actionType() != ActionType.ENSURE_MR) return Optional.empty();
        var operation = confirmedAppliedTaskOperation(claim, "CREATE_MR");
        if (operation.isEmpty()) return Optional.empty();
        var op = operation.get();
        var metadata = em.find(RunItemJpaEntity.class, claim.itemId()).getMetadata();
        String source = metadata.get("plan.featureBranch");
        String targetBranch = metadata.get("plan.releaseBranch");
        JsonNode target = objectJson(op.getTargetJson());
        if (target == null || !matches(target, "source_branch", source)
                || !matches(target, "target_branch", targetBranch)
                || !target.path("sha").isTextual() || !validSha(target.path("sha").asText())) return Optional.empty();
        JsonNode evidence = confirmedAppliedResponse(op);
        if (evidence == null) return Optional.empty();
        return GitLabMergeRequestAdapter.confirmedCreatedMr(evidence.path("httpStatus").intValue(), evidence,
                        op.getProjectId(), source, targetBranch, target.path("sha").asText())
                .map(mr -> new MrEvidence(mr.projectId(), mr.sourceBranch(), mr.targetBranch(), mr.id(), mr.url(),
                        mr.sourceSha(), null, MrDisposition.CREATED, null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MrEvidence> confirmedAppliedMergedMr(RunPort.TaskClaim claim) {
        if (claim == null || claim.actionType() != ActionType.TRY_MERGE) return Optional.empty();
        var operation = confirmedAppliedTaskOperation(claim, "MERGE_MR");
        if (operation.isEmpty()) return Optional.empty();
        var op = operation.get();
        var metadata = em.find(RunItemJpaEntity.class, claim.itemId()).getMetadata();
        MrEvidence checkpoint;
        try {
            checkpoint = RunPort.mrCheckpoint(metadata);
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
        if (!GitMergeWorkflow.isValidCheckpoint(checkpoint)
                || !java.util.Set.of(MrDisposition.CREATED, MrDisposition.MR_REUSED).contains(checkpoint.disposition())
                || !op.getProjectId().equals(checkpoint.projectId())
                || !Objects.equals(metadata.get("plan.featureBranch"), checkpoint.sourceBranch())
                || !Objects.equals(metadata.get("plan.releaseBranch"), checkpoint.targetBranch())) return Optional.empty();
        JsonNode target = objectJson(op.getTargetJson());
        if (target == null || !matches(target, "method", "PUT")
                || !matches(target, "suffix", "/merge_requests/" + checkpoint.iid() + "/merge")
                || !target.path("sha").isTextual() || !validSha(target.path("sha").textValue())
                || !checkpoint.sourceSha().equalsIgnoreCase(target.path("sha").textValue())) return Optional.empty();
        JsonNode evidence = confirmedAppliedResponse(op);
        if (evidence == null) return Optional.empty();
        return GitLabMergeRequestAdapter.confirmedMergedMr(evidence.path("httpStatus").intValue(), evidence,
                        op.getProjectId(), checkpoint.iid(), checkpoint.sourceSha())
                .filter(mr -> checkpoint.sourceBranch().equals(mr.sourceBranch())
                        && checkpoint.targetBranch().equals(mr.targetBranch()))
                .map(mr -> new MrEvidence(checkpoint.projectId(), checkpoint.sourceBranch(), checkpoint.targetBranch(),
                        checkpoint.iid(), checkpoint.url(), checkpoint.sourceSha(), mr.mergeSha(),
                        checkpoint.disposition(), null));
    }

    private Optional<GitOperationEntity> confirmedAppliedTaskOperation(RunPort.TaskClaim claim, String action) {
        var task = lockTask(claim.fence(), true);
        var run = em.find(RunJpaEntity.class, task.getRunId());
        var item = em.find(RunItemJpaEntity.class, task.getTargetId());
        if (item == null || !task.getRunId().equals(claim.runId()) || !item.getId().equals(claim.itemId())
                || !item.getRun().getId().equals(claim.runId()) || !item.getRepoId().equals(claim.repo().value())
                || !task.getTaskType().equals(claim.actionType().name())
                || !run.getOperator().equals(claim.operator())) return Optional.empty();
        var metadata = item.getMetadata();
        if (!"GITLAB".equals(metadata.get("plan.gitProvider"))
                || claim.actionType() == ActionType.ENSURE_FEATURE
                && !java.util.Set.of("AUTO", "NAMED").contains(metadata.getOrDefault("plan.featureBranchMode", ""))) {
            return Optional.empty();
        }
        var operations = em.createQuery("select o from GitOperationEntity o where o.taskId=:task", GitOperationEntity.class)
                .setParameter("task", task.getId()).getResultList();
        if (operations.size() != 1) return Optional.empty();
        var op = operations.getFirst();
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        if (!"APPLIED".equals(op.getState()) || !action.equals(op.getAction())
                || !"GITLAB".equals(op.getProvider()) || !claim.runId().equals(op.getRunId())
                || !claim.repo().value().equals(op.getRepoId()) || !claim.operator().equals(op.getOperator())
                || !historicalOwner(op)
                || (("MERGE_MR".equals(action) || "CREATE_BRANCH".equals(action) || "CREATE_MR".equals(action)) ? !sentBy(op, claim.fence())
                    : !claim.fence().claimToken().equals(op.getClaimToken()))
                || op.getProjectId() == null || !op.getProjectId().matches("[1-9][0-9]*")
                || op.getBaseUrl() == null || op.getBaseUrl().isBlank()
                || !GitOperationJournalPort.configuredBaseUrl(op.getBaseUrl()).equals(op.getBaseUrl())
                || !GitOperationJournalPort.remoteKey(op.getProvider(), op.getBaseUrl(), op.getProjectId())
                        .equals(op.getRemoteKey())
                || !consistentProject(claim.runId(), claim.repo().value(), op.getProjectId(), op.getRemoteKey())) {
            return Optional.empty();
        }
        var guard = matchingGuard(op);
        if (guard.isEmpty() || guard.get().getReason() != null) return Optional.empty();
        return Optional.of(op);
    }

    private JsonNode confirmedAppliedResponse(GitOperationEntity op) {
        var responses = em.createQuery("select e.evidenceJson from GitOperationEventEntity e "
                        + "where e.operationId=:operation and e.attemptId=:attempt and e.sequence=2 and e.type='APPLIED'",
                        String.class)
                .setParameter("operation", op.getId()).setParameter("attempt", op.getAttemptId()).getResultList();
        if (responses.size() != 1) return null;
        JsonNode evidence = objectJson(responses.getFirst());
        if (evidence == null || !evidence.path("httpStatus").isIntegralNumber()
                || !evidence.path("httpStatus").canConvertToInt()) return null;
        return evidence;
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void acknowledgeTask(RunPort.TaskFence fence, RunItemResult result, Map<String, String> resultMetadata) {
        if (result == null || resultMetadata == null) throw invalid();
        var task = lockTask(fence, true);
        var operations = em.createQuery("select o from GitOperationEntity o where o.taskId=:task", GitOperationEntity.class)
                .setParameter("task", fence.taskId()).getResultList();
        if (operations.isEmpty()) return;
        if (operations.size() != 1) throw stale();
        var op = operations.get(0);
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        boolean usesCurrentSender = "MERGE_MR".equals(op.getAction()) || "CREATE_BRANCH".equals(op.getAction())
                || "CREATE_MR".equals(op.getAction());
        if (!taskOperationIdentity(task, op) || !historicalOwner(op)
                || (usesCurrentSender
                    ? "INTENT_RECORDED".equals(op.getState()) ? !onlyIntentEvent(op) : !sentBy(op, fence)
                    : !fence.claimToken().equals(op.getClaimToken()))) throw stale();
        var guard = ownGuard(op);
        boolean unsentOperation = usesCurrentSender && "INTENT_RECORDED".equals(op.getState());
        // A committed SEND owns the right to record its response before any local task result.
        if (usesCurrentSender && "SEND_STARTED".equals(op.getState())) throw stale();
        if (unsentOperation) {
            var item = em.find(RunItemJpaEntity.class, task.getTargetId());
            if (!validUnsentOperation(task, item, op)) throw stale();
            boolean validResult = "CREATE_BRANCH".equals(op.getAction())
                    ? validUnsentBranchResult(op, result, resultMetadata)
                    : "CREATE_MR".equals(op.getAction()) ? validUnsentCreateMrResult(op, result, resultMetadata)
                    : validUnsentMergeResult(result, resultMetadata);
            if (!validResult) throw stale();
            String reason = resultMetadata.get("git.blockReason");
            var evidence = JSON.createObjectNode().put("taskId", fence.taskId())
                    .put("claimToken", fence.claimToken()).put("result", result.name());
            evidence.set("metadata", JSON.valueToTree(resultMetadata));
            op.setState("NOT_SENT");
            op.setReason(reason);
            op.setUpdatedAt(now());
            event(op, 1, "NOT_SENT", evidence.toString(), reason);
        }
        boolean unresolved = List.of("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN", "NEEDS_REVIEW").contains(op.getState());
        if (unresolved) {
            if (result != RunItemResult.UNKNOWN) throw stale();
            guard.setReason("REMOTE_RESULT_UNKNOWN");
        } else if ("APPLIED".equals(op.getState()) && result == RunItemResult.UNKNOWN) {
            op.setState("NEEDS_REVIEW");
            op.setReason("LOCAL_RESULT_UNCONFIRMED");
            op.setUpdatedAt(now());
            event(op, 3, "LOCAL_RESULT_UNCONFIRMED", "{}", "LOCAL_RESULT_UNCONFIRMED");
            guard.setReason("LOCAL_RESULT_UNCONFIRMED");
        } else {
            if (!unsentOperation && !"REJECTED".equals(op.getState())
                    && !("APPLIED".equals(op.getState()) && confirmsAppliedTask(task.getTaskType(), result))) throw stale();
            guard.setSessionId(null);
            guard.setOperationId(null);
            guard.setReason(null);
        }
        guard.setUpdatedAt(now());
        em.flush();
    }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW, readOnly=true)
    public List<Operation> list(String repoId, int page, int size) {
        return em.createQuery("select o from GitOperationEntity o where o.repoId=:repo order by o.createdAt desc, o.id desc", GitOperationEntity.class)
                .setParameter("repo", repoId).setFirstResult(Math.max(0, page) * Math.min(50, Math.max(1,size)))
                .setMaxResults(Math.min(50,Math.max(1,size))).getResultList().stream().map(this::view).toList();
    }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW, readOnly=true)
    public List<Operation> listByRun(String runId, int page, int size) {
        return em.createQuery("select o from GitOperationEntity o where o.runId=:run order by o.createdAt desc, o.id desc", GitOperationEntity.class)
                .setParameter("run", runId).setFirstResult(Math.max(0,page)*Math.min(50,Math.max(1,size)))
                .setMaxResults(Math.min(50,Math.max(1,size))).getResultList().stream().map(this::view).toList();
    }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW, readOnly=true)
    public Optional<Detail> find(String id) {
        var op = em.find(GitOperationEntity.class,id); if (op == null) return Optional.empty();
        var events = em.createQuery("select e from GitOperationEventEntity e where e.operationId=:id order by e.sequence",GitOperationEventEntity.class)
                .setParameter("id",id).setMaxResults(100).getResultList().stream()
                .map(e -> new Event(e.getAttemptId(),e.getSequence(),e.getType(),e.getAt(),e.getEvidenceJson(),e.getReason())).toList();
        var observations = em.createQuery("select o from GitOperationObservationEntity o where o.operationId=:id order by o.observedAt desc, o.id desc",GitOperationObservationEntity.class)
                .setParameter("id",id).setMaxResults(51).getResultList();
        return Optional.of(new Detail(view(op),events,observations.stream().limit(50).map(this::view).toList(),observations.size()>50));
    }

    @Override public Optional<Observation> observation(String id,String operator,String key) {
        return em.createQuery("select o from GitOperationObservationEntity o where o.operationId=:id and o.operator=:operator and o.key=:key",GitOperationObservationEntity.class)
                .setParameter("id",id).setParameter("operator",operator).setParameter("key",key).getResultStream().findFirst().map(this::view);
    }

    @Override public Observation observe(String id,String operator,String key,long expected,GitReadResult.Status status,String evidence) {
        lock("observe:"+id+":"+operator+":"+key);
        var prior=observation(id,operator,key);
        if (prior.isPresent()) {
            if (prior.get().expectedVersion()!=expected) throw stale();
            return prior.get();
        }
        var op=operationForObservation(id); if (op.getVersion()!=expected) throw stale();
        var row=new GitOperationObservationEntity(); row.setId(UUID.randomUUID().toString()); row.setOperationId(id);
        row.setOperator(operator); row.setKey(key); row.setExpectedVersion(expected); row.setStatus(status.name());
        row.setEvidenceJson(evidence); row.setObservedAt(now()); em.persist(row);
        // S2 只记录观察，不解除保留或重放；本地业务结果与在途请求仍可能未知。
        op.setUpdatedAt(now()); em.lock(op,LockModeType.OPTIMISTIC_FORCE_INCREMENT); em.flush();
        return view(row);
    }

    @Override public boolean hasActiveGuards() {
        return em.createQuery("select count(g) from RepoExecutionGuardEntity g where g.sessionId is not null",Long.class).getSingleResult()>0;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockMappingChanges() {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended('git-mapping-admission', 2))",(RowCallbackHandler) r->{});
    }
    private RepoExecutionGuardEntity ownGuard(GitOperationEntity op) {
        return matchingGuard(op).orElseThrow(this::stale);
    }

    private Optional<RepoExecutionGuardEntity> matchingGuard(GitOperationEntity op) {
        lock(op.getRemoteKey());
        var guard = em.find(RepoExecutionGuardEntity.class, op.getRemoteKey(), LockModeType.PESSIMISTIC_WRITE);
        if (guard != null) em.refresh(guard, LockModeType.PESSIMISTIC_WRITE);
        if (guard == null || !Objects.equals(op.getSessionId(), guard.getSessionId())
                || op.getGeneration() != guard.getGeneration() || !op.getId().equals(guard.getOperationId())) {
            return Optional.empty();
        }
        return Optional.of(guard);
    }
    private GitOperationEntity operationForUpdate(String id, RunPort.TaskFence fence) {
        var op = em.find(GitOperationEntity.class, id);
        if (op == null) throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, id);
        RunTaskJpaEntity task = null;
        if (op.getTaskId() != null) {
            if (fence == null || !op.getTaskId().equals(fence.taskId())) throw stale();
            task = lockTask(fence, true);
        } else if (fence != null) throw stale();
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        if (task != null && (!taskOperationIdentity(task, op) || !historicalOwner(op)
                || !("MERGE_MR".equals(op.getAction()) || "CREATE_BRANCH".equals(op.getAction()) || "CREATE_MR".equals(op.getAction()))
                    && !fence.claimToken().equals(op.getClaimToken()))) throw stale();
        return op;
    }

    private GitOperationEntity operationForObservation(String id) {
        var op = em.find(GitOperationEntity.class, id);
        if (op == null) throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, id);
        var task = op.getTaskId() == null ? null : lockTaskOwner(op.getTaskId(), false);
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        if (task != null && (!taskOperationIdentity(task, op) || !historicalOwner(op))) throw stale();
        return op;
    }

    private RunTaskJpaEntity lockTask(RunPort.TaskFence fence, boolean requireRunning) {
        if (fence == null || fence.taskId() == null || fence.claimToken() == null
                || fence.claimToken().isBlank()) throw stale();
        var task = lockTaskOwner(fence.taskId(), requireRunning);
        if (!fence.claimToken().equals(task.getClaimToken())) throw stale();
        return task;
    }

    private RunTaskJpaEntity lockTaskOwner(String taskId, boolean requireRunning) {
        String runId = em.createQuery("select t.runId from RunTaskJpaEntity t where t.id=:id", String.class)
                .setParameter("id", taskId).getResultStream().findFirst().orElseThrow(this::stale);
        var run = em.find(RunJpaEntity.class, runId, LockModeType.PESSIMISTIC_WRITE);
        var task = em.find(RunTaskJpaEntity.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (run != null) em.refresh(run, LockModeType.PESSIMISTIC_WRITE);
        if (task != null) em.refresh(task, LockModeType.PESSIMISTIC_WRITE);
        if (run == null || task == null || !runId.equals(task.getRunId())
                || !"WINDOW_ORCHESTRATION".equals(run.getRunType())
                || !"RUN_ITEM".equals(task.getTargetType())
                || !Integer.valueOf(io.releasehub.domain.run.Run.ORCHESTRATION_PROTOCOL_VERSION).equals(run.getExecutionVersion())
                || requireRunning && (!"RUNNING".equals(task.getStatus())
                || !java.util.Set.of("RUNNING", "NEEDS_REVIEW").contains(run.getExecutionStatus()))) throw stale();
        return task;
    }

    private List<GitOperationEntity> taskOperations(String taskId) {
        return em.createQuery("select o from GitOperationEntity o where o.taskId=:task", GitOperationEntity.class)
                .setParameter("task", taskId).getResultList();
    }

    private boolean historicalOwner(GitOperationEntity op) {
        return op.getClaimToken() != null && !op.getClaimToken().isBlank()
                && op.getClaimToken().equals(op.getSessionId());
    }

    private boolean taskOperationIdentity(RunTaskJpaEntity task, GitOperationEntity op) {
        var run = em.find(RunJpaEntity.class, task.getRunId());
        var item = em.find(RunItemJpaEntity.class, task.getTargetId());
        String action = switch (task.getTaskType()) {
            case "ENSURE_FEATURE", "ENSURE_RELEASE" -> "CREATE_BRANCH";
            case "ENSURE_MR" -> "CREATE_MR";
            case "TRY_MERGE" -> "MERGE_MR";
            default -> "";
        };
        return item != null && task.getId().equals(op.getTaskId()) && task.getRunId().equals(op.getRunId())
                && task.getRunId().equals(item.getRun().getId()) && item.getRepoId().equals(op.getRepoId())
                && run.getOperator().equals(op.getOperator()) && action.equals(op.getAction());
    }

    private boolean validCreateMrIdentity(String provider, String baseUrl, String projectId, String remoteKey) {
        return "GITLAB".equals(provider) && projectId != null && projectId.matches("[1-9][0-9]*")
                && baseUrl != null && !baseUrl.isBlank()
                && GitOperationJournalPort.configuredBaseUrl(baseUrl).equals(baseUrl)
                && GitOperationJournalPort.remoteKey(provider, baseUrl, projectId).equals(remoteKey);
    }

    private boolean completeCreateMrRequest(String operationId, JsonNode target, String remoteKey, String requestHash) {
        var body = GitLabMergeRequestAdapter.recordedCreateMrRequest(target, operationId);
        if (body == null) return false;
        String fingerprint = GitLabMergeRequestAdapter.createMrRequestHash(remoteKey, body, target.path("sha").textValue());
        return fingerprint != null && fingerprint.equals(requestHash);
    }

    private boolean validCreateMrTaskPlan(RunTaskJpaEntity task, RunItemJpaEntity item, JsonNode target) {
        if (item == null || !"ENSURE_MR".equals(task.getTaskType())
                || task.getStartedAt() == null || task.getClaimToken() == null || task.getClaimToken().isBlank()
                || task.getClaimExpiresAt() == null || task.getFinishedAt() != null || item.getExecutedOrder() <= 0
                || !"GITLAB".equals(item.getMetadata().get("plan.gitProvider"))
                || !matches(target, "source_branch", item.getMetadata().get("plan.featureBranch"))
                || !matches(target, "target_branch", item.getMetadata().get("plan.releaseBranch"))) return false;
        Number steps = (Number) em.createNativeQuery("select count(*) from {h-schema}run_step where task_id=?1")
                .setParameter(1, task.getId()).getSingleResult();
        return steps.longValue() == 0;
    }

    /** 同一份完整原意图证明覆盖 Task 接管、发送和本地未发送结果；非 Task 会话仍自行持有意图。 */
    private boolean validCreateMrIntent(GitOperationEntity op, RepoExecutionGuardEntity guard) {
        JsonNode target = objectJson(op.getTargetJson());
        if (!"CREATE_MR".equals(op.getAction()) || !"INTENT_RECORDED".equals(op.getState()) || op.getReason() != null
                || op.getSessionId() == null || op.getSessionId().isBlank()
                || !validCreateMrIdentity(op.getProvider(), op.getBaseUrl(), op.getProjectId(), op.getRemoteKey())
                || !completeCreateMrRequest(op.getId(), target, op.getRemoteKey(), op.getRequestHash())
                || !"INTENT_PENDING".equals(guard.getReason()) || !onlyIntentEvent(op)) return false;
        if (op.getTaskId() == null) {
            return op.getClaimToken() == null && (op.getRunId() == null
                    || em.createQuery("select count(t) from RunTaskJpaEntity t where t.runId=:run", Long.class)
                    .setParameter("run", op.getRunId()).getSingleResult() == 0);
        }
        var task = em.find(RunTaskJpaEntity.class, op.getTaskId());
        if (task == null) return false;
        var item = em.find(RunItemJpaEntity.class, task.getTargetId());
        var operations = taskOperations(task.getId());
        return operations.size() == 1 && op.getId().equals(operations.getFirst().getId())
                && validUnsentOperation(task, item, op);
    }

    private boolean validUnsentOperation(RunTaskJpaEntity task, RunItemJpaEntity item, GitOperationEntity op) {
        if (item == null || !java.util.Set.of("TRY_MERGE", "ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR").contains(task.getTaskType())
                || !taskOperationIdentity(task, op)
                || task.getStartedAt() == null || task.getClaimToken() == null || task.getClaimToken().isBlank()
                || task.getClaimExpiresAt() == null
                || task.getFinishedAt() != null || item.getExecutedOrder() <= 0 || !historicalOwner(op)
                || !"INTENT_RECORDED".equals(op.getState()) || op.getReason() != null
                || !"GITLAB".equals(op.getProvider()) || !"GITLAB".equals(item.getMetadata().get("plan.gitProvider"))
                || op.getProjectId() == null || !op.getProjectId().matches("[1-9][0-9]*")
                || op.getBaseUrl() == null || op.getBaseUrl().isBlank()
                || !GitOperationJournalPort.configuredBaseUrl(op.getBaseUrl()).equals(op.getBaseUrl())
                || !GitOperationJournalPort.remoteKey(op.getProvider(), op.getBaseUrl(), op.getProjectId()).equals(op.getRemoteKey())
                || !consistentProject(task.getRunId(), item.getRepoId(), op.getProjectId(), op.getRemoteKey())
                || !onlyIntentEvent(op)) return false;
        Number steps = (Number) em.createNativeQuery(
                        "select count(*) from {h-schema}run_step where task_id=?1")
                .setParameter(1, task.getId()).getSingleResult();
        if (steps.longValue() != 0) return false;
        boolean completeRequest = switch (op.getAction()) {
            case "CREATE_BRANCH" -> completeBranchRequest(task, op, item.getMetadata());
            case "CREATE_MR" -> completeCreateMrRequest(op.getId(), objectJson(op.getTargetJson()), op.getRemoteKey(), op.getRequestHash())
                    && validCreateMrTaskPlan(task, item, objectJson(op.getTargetJson()));
            case "MERGE_MR" -> completeMergeRequest(op, item.getMetadata());
            default -> false;
        };
        if (!completeRequest) return false;
        var guard = matchingGuard(op);
        return guard.isPresent() && "INTENT_PENDING".equals(guard.get().getReason());
    }

    private boolean completeBranchRequest(RunTaskJpaEntity task, GitOperationEntity op, Map<String, String> metadata) {
        if ("ENSURE_FEATURE".equals(task.getTaskType())) {
            String mode = metadata.get("plan.featureBranchMode");
            if (!"AUTO".equals(mode) && !"NAMED".equals(mode)) return false;
        }
        String branch = switch (task.getTaskType()) {
            case "ENSURE_FEATURE" -> metadata.get("plan.featureBranch");
            case "ENSURE_RELEASE" -> metadata.get("plan.releaseBranch");
            default -> null;
        };
        JsonNode target = objectJson(op.getTargetJson());
        JsonNode body = GitLabGitBranchAdapter.recordedBranchRequest(target);
        if (body == null || !matches(target, "branch", branch)
                || !matches(target, "sourceRef", metadata.get("plan.defaultBranch"))) return false;
        String fingerprint = GitLabGitBranchAdapter.branchRequestHash(op.getRemoteKey(), body);
        return fingerprint != null && fingerprint.equals(op.getRequestHash());
    }

    private boolean validUnsentBranchResult(GitOperationEntity op, RunItemResult result, Map<String, String> patch) {
        if (!java.util.Set.of("git.projectId", "git.blockReason", "git.requestId").containsAll(patch.keySet())
                || patch.get("git.requestId") != null
                || patch.containsKey("git.projectId") && !op.getProjectId().equals(patch.get("git.projectId"))) return false;
        String reason = patch.get("git.blockReason");
        if (result == RunItemResult.ALREADY_EXISTS) return reason == null;
        return (result == RunItemResult.FAILED || result == RunItemResult.UNKNOWN)
                && (reason == null || java.util.Arrays.stream(MergeBlockReason.values())
                        .anyMatch(value -> value.name().equals(reason)));
    }

    private boolean validUnsentCreateMrResult(GitOperationEntity op, RunItemResult result, Map<String, String> patch) {
        if (!java.util.Set.of("git.projectId", "git.sourceBranch", "git.targetBranch", "git.sourceSha",
                        "git.mrIid", "git.mrUrl", "git.mergeSha", "git.mrDisposition", "git.blockReason", "git.requestId")
                .containsAll(patch.keySet()) || !patch.containsKey("git.blockReason")
                || patch.get("git.requestId") != null || patch.get("git.mergeSha") != null) return false;
        JsonNode target = objectJson(op.getTargetJson());
        if (patch.containsKey("git.projectId") && !op.getProjectId().equals(patch.get("git.projectId"))
                || patch.containsKey("git.sourceBranch") && !matches(target, "source_branch", patch.get("git.sourceBranch"))
                || patch.containsKey("git.targetBranch") && !matches(target, "target_branch", patch.get("git.targetBranch"))
                || patch.containsKey("git.sourceSha") && (patch.get("git.sourceSha") == null
                    || !target.path("sha").textValue().equalsIgnoreCase(patch.get("git.sourceSha")))) return false;
        String reason = patch.get("git.blockReason");
        if (result == RunItemResult.MR_REUSED || result == RunItemResult.ALREADY_MERGED) {
            if (reason != null || !result.name().equals(patch.get("git.mrDisposition"))) return false;
            return GitMergeWorkflow.isValidCheckpoint(RunPort.mrCheckpoint(patch));
        }
        // Explicit nulls delete stale checkpoint fields; failures must never invent a reusable MR.
        if (patch.get("git.mrIid") != null || patch.get("git.mrUrl") != null
                || patch.get("git.mrDisposition") != null) return false;
        return switch (result) {
            case UNKNOWN -> "UNKNOWN".equals(reason);
            case FAILED -> reason == null || "UNKNOWN".equals(reason);
            case MERGE_BLOCKED -> reason != null && !"UNKNOWN".equals(reason)
                    && java.util.Arrays.stream(MergeBlockReason.values()).anyMatch(value -> value.name().equals(reason));
            default -> false;
        };
    }

    private boolean validUnsentMergeResult(RunItemResult result, Map<String, String> patch) {
        if (!java.util.Set.of("git.requestId", "git.blockReason", "git.mergeSha", "git.mrDisposition")
                .containsAll(patch.keySet()) || !patch.containsKey("git.blockReason")
                || patch.get("git.requestId") != null) return false;
        String reason = patch.get("git.blockReason");
        if (result == RunItemResult.ALREADY_MERGED) {
            String mergeSha = patch.get("git.mergeSha");
            return "ALREADY_MERGED".equals(patch.get("git.mrDisposition"))
                    && patch.containsKey("git.mergeSha") && reason == null
                    && (mergeSha == null || validSha(mergeSha));
        }
        if (patch.containsKey("git.mergeSha") || patch.containsKey("git.mrDisposition")) return false;
        // Validate result data; the complete no-SEND proof above authorizes local termination.
        return result == RunItemResult.UNKNOWN && "UNKNOWN".equals(reason)
                || result == RunItemResult.MERGE_BLOCKED && java.util.Arrays.stream(MergeBlockReason.values())
                        .anyMatch(value -> value.name().equals(reason));
    }

    private boolean completeMergeRequest(GitOperationEntity op, java.util.Map<String, String> metadata) {
        MrEvidence checkpoint;
        try { checkpoint = RunPort.mrCheckpoint(metadata); }
        catch (IllegalArgumentException error) { return false; }
        if (!GitMergeWorkflow.isValidCheckpoint(checkpoint)
                || !java.util.Set.of(MrDisposition.CREATED, MrDisposition.MR_REUSED).contains(checkpoint.disposition())
                || !op.getProjectId().equals(checkpoint.projectId())
                || !Objects.equals(metadata.get("plan.featureBranch"), checkpoint.sourceBranch())
                || !Objects.equals(metadata.get("plan.releaseBranch"), checkpoint.targetBranch())) return false;
        JsonNode target = objectJson(op.getTargetJson());
        if (target == null || target.size() != 5 || !matches(target, "method", "PUT")
                || !matches(target, "suffix", "/merge_requests/" + checkpoint.iid() + "/merge")
                || !matches(target, "sha", checkpoint.sourceSha())
                || !matches(target, "merge_commit_message",
                    "Merge " + checkpoint.sourceBranch() + " into " + checkpoint.targetBranch())) return false;
        var body = JSON.createObjectNode();
        for (String key : List.of("sha", "merge_commit_message", "should_remove_source_branch")) {
            if (target.has(key)) body.set(key, target.get(key));
        }
        String fingerprint = GitLabMergeRequestAdapter.mergeRequestHash(op.getRemoteKey(), target.path("suffix").textValue(), body);
        return fingerprint != null && fingerprint.equals(op.getRequestHash());
    }

    private List<GitOperationEventEntity> operationEvents(GitOperationEntity op) {
        return em.createQuery("select e from GitOperationEventEntity e where e.operationId=:operation order by e.sequence",
                        GitOperationEventEntity.class)
                .setParameter("operation", op.getId()).getResultList();
    }

    private boolean intentEvent(GitOperationEntity op, GitOperationEventEntity event) {
        JsonNode evidence = objectJson(event.getEvidenceJson());
        return op.getAttemptId() != null && !op.getAttemptId().isBlank()
                && op.getAttemptId().equals(event.getAttemptId()) && event.getSequence() == 0
                && "INTENT_RECORDED".equals(event.getType()) && event.getReason() == null
                && evidence != null && evidence.isEmpty();
    }

    private boolean onlyIntentEvent(GitOperationEntity op) {
        var events = operationEvents(op);
        return events.size() == 1 && intentEvent(op, events.getFirst());
    }

    private boolean sentBy(GitOperationEntity op, RunPort.TaskFence fence) {
        if (fence == null || !Objects.equals(op.getTaskId(), fence.taskId())) return false;
        var events = operationEvents(op);
        int count = switch (op.getState()) {
            case "SEND_STARTED" -> 2;
            case "APPLIED", "REJECTED", "UNKNOWN" -> 3;
            case "NEEDS_REVIEW" -> 4;
            default -> 0;
        };
        if (events.size() != count || count < 2 || !intentEvent(op, events.getFirst())) return false;
        for (int index = 0; index < events.size(); index++) {
            var event = events.get(index);
            if (event.getSequence() != index || !op.getAttemptId().equals(event.getAttemptId())) return false;
        }
        var send = events.get(1);
        if (!"SEND_STARTED".equals(send.getType()) || send.getReason() != null) return false;
        JsonNode sender = objectJson(send.getEvidenceJson());
        if (sender == null || sender.size() != 2 || !matches(sender, "taskId", fence.taskId())
                || !matches(sender, "claimToken", fence.claimToken())) return false;
        if (count == 2) return true;
        return ("NEEDS_REVIEW".equals(op.getState())
                ? "APPLIED".equals(events.get(2).getType()) && "LOCAL_RESULT_UNCONFIRMED".equals(events.get(3).getType())
                : op.getState().equals(events.get(2).getType()));
    }

    private void validateTaskCommand(Command command) {
        if (command.taskFence() == null) {
            if (command.runId() != null && em.createQuery("select count(t) from RunTaskJpaEntity t where t.runId=:run", Long.class)
                    .setParameter("run", command.runId()).getSingleResult() > 0) throw stale();
            return;
        }
        var task = lockTask(command.taskFence(), true);
        var run = em.find(RunJpaEntity.class, task.getRunId());
        var item = em.find(RunItemJpaEntity.class, task.getTargetId());
        if (!task.getRunId().equals(command.runId()) || !run.getOperator().equals(command.operator())
                || !command.taskFence().claimToken().equals(command.sessionId())
                || item == null || !task.getRunId().equals(item.getRun().getId())
                || !item.getRepoId().equals(command.repoId())) throw stale();
        var metadata = item.getMetadata();
        if (!consistentProject(command.runId(), command.repoId(), command.projectId(), command.remoteKey())) throw stale();
        com.fasterxml.jackson.databind.JsonNode target;
        try { target = JSON.readTree(command.targetJson()); }
        catch (java.io.IOException | IllegalArgumentException error) { throw invalid(); }
        if (target == null || !target.isObject()) throw invalid();
        boolean matches = switch (task.getTaskType()) {
            case "ENSURE_FEATURE" -> !"EXISTING".equals(metadata.get("plan.featureBranchMode"))
                    && branchCommand(command, target, metadata.get("plan.featureBranch"), metadata.get("plan.defaultBranch"));
            case "ENSURE_RELEASE" -> branchCommand(command, target, metadata.get("plan.releaseBranch"), metadata.get("plan.defaultBranch"));
            case "ENSURE_MR" -> "CREATE_MR".equals(command.action())
                    && validCreateMrTaskPlan(task, item, target);
            case "TRY_MERGE" -> "MERGE_MR".equals(command.action())
                    && command.projectId().equals(metadata.get("git.projectId"))
                    && matches(target, "sha", metadata.get("git.sourceSha"))
                    && matches(target, "suffix", "/merge_requests/" + metadata.get("git.mrIid") + "/merge")
                    && Objects.equals(metadata.get("plan.featureBranch"), metadata.get("git.sourceBranch"))
                    && Objects.equals(metadata.get("plan.releaseBranch"), metadata.get("git.targetBranch"));
            default -> false;
        };
        if (!matches) throw invalid();
    }

    private boolean consistentProject(String runId, String repoId, String projectId, String remoteKey) {
        long changedReadIdentity = em.createQuery("select count(i) from RunItemJpaEntity i join i.metadata m "
                        + "where i.run.id=:run and i.repoId=:repo and key(m)='git.projectId' and value(m)<>:project", Long.class)
                .setParameter("run", runId).setParameter("repo", repoId).setParameter("project", projectId).getSingleResult();
        if (changedReadIdentity != 0) return false;
        long changedProject = em.createQuery(
                        "select count(o) from GitOperationEntity o where o.runId=:run and o.repoId=:repo and o.remoteKey<>:remote",
                        Long.class)
                .setParameter("run", runId).setParameter("repo", repoId).setParameter("remote", remoteKey).getSingleResult();
        return changedProject == 0;
    }

    private JsonNode objectJson(String value) {
        if (value == null) return null;
        try {
            JsonNode parsed = JSON.readTree(value);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            return null;
        }
    }

    private boolean branchCommand(Command command, com.fasterxml.jackson.databind.JsonNode target, String branch, String base) {
        return "CREATE_BRANCH".equals(command.action()) && matches(target, "branch", branch)
                && matches(target, "sourceRef", base) && validSha(target.path("ref").asText());
    }

    private boolean matches(com.fasterxml.jackson.databind.JsonNode target, String field, String expected) {
        return expected != null && !expected.isBlank() && target.path(field).isTextual()
                && expected.equals(target.path(field).asText());
    }

    private boolean validSha(String value) { return value != null && value.matches("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})"); }

    private boolean confirmsAppliedTask(String action, RunItemResult result) {
        return switch (action) {
            case "ENSURE_FEATURE", "ENSURE_RELEASE" -> result == RunItemResult.CREATED || result == RunItemResult.ALREADY_EXISTS;
            case "ENSURE_MR" -> result == RunItemResult.CREATED || result == RunItemResult.MR_REUSED || result == RunItemResult.ALREADY_MERGED;
            case "TRY_MERGE" -> result == RunItemResult.MERGED || result == RunItemResult.ALREADY_MERGED;
            default -> false;
        };
    }

    private void event(GitOperationEntity op,int sequence,String type,String evidence,String reason) {
        var e=new GitOperationEventEntity();e.setId(UUID.randomUUID().toString());e.setOperationId(op.getId());
        e.setAttemptId(op.getAttemptId());e.setSequence(sequence);e.setType(type);e.setAt(now());
        e.setEvidenceJson(evidence==null?"{}":evidence);e.setReason(reason);em.persist(e);
    }
    private Operation view(GitOperationEntity o) {
        var g=em.find(RepoExecutionGuardEntity.class,o.getRemoteKey());
        return new Operation(o.getId(),o.getRepoId(),o.getRemoteKey(),o.getProvider(),o.getBaseUrl(),o.getProjectId(),
                o.getAction(),o.getTargetJson(),o.getRunId(),o.getOperator(),o.getState(),o.getVersion(),o.getCreatedAt(),o.getUpdatedAt(),o.getReason(),g!=null&&g.getSessionId()!=null);
    }
    private Observation view(GitOperationObservationEntity o) {
        return new Observation(o.getId(),o.getOperator(),o.getKey(),o.getExpectedVersion(),o.getStatus(),o.getEvidenceJson(),o.getObservedAt());
    }
    private void lock(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 1))",ps->ps.setString(1,key),(RowCallbackHandler) r->{});
    }
    private Instant now(){return clock.instant();}
    private BusinessException invalid(){return BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);}
    private BusinessException blocked(){return BusinessException.of(ErrorCode.GIT_WRITE_BLOCKED);}
    private BusinessException stale(){return BusinessException.of(ErrorCode.GIT_OPERATION_STALE);}
}
