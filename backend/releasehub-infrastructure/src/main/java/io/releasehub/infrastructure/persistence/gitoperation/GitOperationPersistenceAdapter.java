package io.releasehub.infrastructure.persistence.gitoperation;

import com.fasterxml.jackson.databind.JsonNode;
import io.releasehub.infrastructure.git.GitLabGitBranchAdapter;
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
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

/** 每次证据变更独立提交，即使调用者的业务事务随后回滚也保留。 */
@Repository @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GitOperationPersistenceAdapter implements GitOperationJournalPort {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final io.releasehub.application.repo.CodeRepositoryPort repos;
    private final io.releasehub.application.settings.SettingsPort settings;

    @Override public String begin(Command c) {
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
        var existing = em.find(GitOperationEntity.class, c.operationId());
        if (existing != null) {
            if (!existing.getRequestHash().equals(c.requestHash()) || !existing.getOperator().equals(c.operator())
                    || !Objects.equals(existing.getRunId(), c.runId()) || !existing.getSessionId().equals(c.sessionId())
                    || !Objects.equals(existing.getTaskId(), c.taskFence() == null ? null : c.taskFence().taskId())
                    || !Objects.equals(existing.getClaimToken(), c.taskFence() == null ? null : c.taskFence().claimToken())) throw stale();
            return existing.getId();
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
        em.flush(); return op.getId();
    }

    @Override public void sending(String id) {
        var op = operationForUpdate(id, true); var guard = ownGuard(op);
        if (!"INTENT_RECORDED".equals(op.getState())) throw stale();
        op.setState("SEND_STARTED"); op.setUpdatedAt(now());
        guard.setReason("REQUEST_IN_FLIGHT"); guard.setUpdatedAt(now());
        event(op, 1, "SEND_STARTED", "{}", null); em.flush();
    }

    @Override public void finish(String id, GitWriteResult.Outcome outcome, String evidence, String reason) {
        var op = operationForUpdate(id, true); var guard = ownGuard(op);
        if (!"SEND_STARTED".equals(op.getState())) throw stale();
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
    public Optional<String> confirmedAppliedBranchProject(RunPort.TaskClaim claim) {
        if (claim == null || claim.actionType() != ActionType.ENSURE_FEATURE
                && claim.actionType() != ActionType.ENSURE_RELEASE) return Optional.empty();
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
        if (!"APPLIED".equals(op.getState()) || !"CREATE_BRANCH".equals(op.getAction())
                || !"GITLAB".equals(op.getProvider()) || !claim.runId().equals(op.getRunId())
                || !claim.repo().value().equals(op.getRepoId()) || !claim.operator().equals(op.getOperator())
                || !claim.fence().claimToken().equals(op.getClaimToken())
                || !claim.fence().claimToken().equals(op.getSessionId())
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
        JsonNode target = objectJson(op.getTargetJson());
        String branch = metadata.get(claim.actionType() == ActionType.ENSURE_FEATURE
                ? "plan.featureBranch" : "plan.releaseBranch");
        if (target == null || !matches(target, "branch", branch)
                || !matches(target, "sourceRef", metadata.get("plan.defaultBranch"))
                || !target.path("ref").isTextual() || !validSha(target.path("ref").asText())) return Optional.empty();
        var responses = em.createQuery("select e.evidenceJson from GitOperationEventEntity e "
                        + "where e.operationId=:operation and e.attemptId=:attempt and e.sequence=2 and e.type='APPLIED'",
                        String.class)
                .setParameter("operation", op.getId()).setParameter("attempt", op.getAttemptId()).getResultList();
        if (responses.size() != 1) return Optional.empty();
        JsonNode evidence = objectJson(responses.getFirst());
        if (evidence == null || !evidence.path("httpStatus").isIntegralNumber()
                || !evidence.path("httpStatus").canConvertToInt()) return Optional.empty();
        String sha = GitLabGitBranchAdapter.createdBranchSha(evidence.path("httpStatus").intValue(), evidence, branch);
        if (sha == null || !target.path("ref").asText().equalsIgnoreCase(sha)) return Optional.empty();
        return Optional.of(op.getProjectId());
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void acknowledgeTask(RunPort.TaskFence fence, RunItemResult result) {
        if (result == null) throw invalid();
        var task = lockTask(fence, true);
        var operations = em.createQuery("select o from GitOperationEntity o where o.taskId=:task", GitOperationEntity.class)
                .setParameter("task", fence.taskId()).getResultList();
        if (operations.isEmpty()) return;
        if (operations.size() != 1) throw stale();
        var op = operations.get(0);
        if (!fence.claimToken().equals(op.getClaimToken()) || !fence.claimToken().equals(op.getSessionId())
                || !task.getRunId().equals(op.getRunId())) throw stale();
        lock(op.getRemoteKey());
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        var guard = ownGuard(op);
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
            if (!"REJECTED".equals(op.getState())
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
        var op=operationForUpdate(id, false); if (op.getVersion()!=expected) throw stale();
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
        if (guard == null || !op.getSessionId().equals(guard.getSessionId())
                || op.getGeneration() != guard.getGeneration() || !op.getId().equals(guard.getOperationId())) {
            return Optional.empty();
        }
        return Optional.of(guard);
    }
    private GitOperationEntity operationForUpdate(String id, boolean requireRunning) {
        var op=em.find(GitOperationEntity.class,id);
        if(op==null) throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND,id);
        if (op.getTaskId() != null) lockTask(new RunPort.TaskFence(op.getTaskId(), op.getClaimToken()), requireRunning);
        lock(op.getRemoteKey()); em.refresh(op,LockModeType.PESSIMISTIC_WRITE); return op;
    }

    private RunTaskJpaEntity lockTask(RunPort.TaskFence fence, boolean requireRunning) {
        if (fence == null || fence.taskId() == null || fence.claimToken() == null) throw stale();
        String runId = em.createQuery("select t.runId from RunTaskJpaEntity t where t.id=:id", String.class)
                .setParameter("id", fence.taskId()).getResultStream().findFirst().orElseThrow(this::stale);
        var run = em.find(RunJpaEntity.class, runId, LockModeType.PESSIMISTIC_WRITE);
        var task = em.find(RunTaskJpaEntity.class, fence.taskId(), LockModeType.PESSIMISTIC_WRITE);
        if (run == null || task == null || !runId.equals(task.getRunId())
                || !"WINDOW_ORCHESTRATION".equals(run.getRunType())
                || !"RUN_ITEM".equals(task.getTargetType())
                || !fence.claimToken().equals(task.getClaimToken())
                || !Integer.valueOf(io.releasehub.domain.run.Run.ORCHESTRATION_PROTOCOL_VERSION).equals(run.getExecutionVersion())
                || requireRunning && (!"RUNNING".equals(task.getStatus())
                || !java.util.Set.of("RUNNING", "NEEDS_REVIEW").contains(run.getExecutionStatus()))) throw stale();
        return task;
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
                    && matches(target, "source_branch", metadata.get("plan.featureBranch"))
                    && matches(target, "target_branch", metadata.get("plan.releaseBranch"))
                    && validSha(target.path("sha").asText());
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
