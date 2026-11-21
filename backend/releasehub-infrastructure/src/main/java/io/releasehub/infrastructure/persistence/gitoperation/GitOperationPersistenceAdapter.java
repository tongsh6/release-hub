package io.releasehub.infrastructure.persistence.gitoperation;

import io.releasehub.application.gitoperation.*;
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
import java.util.UUID;

/** 每次证据变更独立提交，即使调用者的业务事务随后回滚也保留。 */
@Repository @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GitOperationPersistenceAdapter implements GitOperationJournalPort {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final io.releasehub.application.repo.CodeRepositoryPort repos;
    private final io.releasehub.application.settings.SettingsPort settings;

    @Override public String begin(Command c) {
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
            if (!existing.getRequestHash().equals(c.requestHash()) || !existing.getOperator().equals(c.operator())) throw stale();
            return existing.getId();
        }
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
        op.setState("INTENT_RECORDED"); op.setCreatedAt(now()); op.setUpdatedAt(now()); em.persist(op);
        guard.setSessionId(c.sessionId()); guard.setOperationId(op.getId());
        guard.setReason("INTENT_PENDING"); guard.setUpdatedAt(now());
        event(op, 0, "INTENT_RECORDED", "{}", null);
        em.flush(); return op.getId();
    }

    @Override public void sending(String id) {
        var op = operationForUpdate(id); var guard = ownGuard(op);
        if (!"INTENT_RECORDED".equals(op.getState())) throw stale();
        op.setState("SEND_STARTED"); op.setUpdatedAt(now());
        guard.setReason("REQUEST_IN_FLIGHT"); guard.setUpdatedAt(now());
        event(op, 1, "SEND_STARTED", "{}", null); em.flush();
    }

    @Override public void finish(String id, GitWriteResult.Outcome outcome, String evidence, String reason) {
        var op = operationForUpdate(id); var guard = ownGuard(op);
        if (!"SEND_STARTED".equals(op.getState())) throw stale();
        op.setState(outcome.name()); op.setReason(reason); op.setUpdatedAt(now());
        guard.setReason(outcome == GitWriteResult.Outcome.UNKNOWN ? "REMOTE_RESULT_UNKNOWN" : null);
        guard.setUpdatedAt(now()); event(op, 2, outcome.name(), evidence, reason); em.flush();
    }

    @Override public void completeSession(String session, boolean committed) {
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
        var op=operationForUpdate(id); if (op.getVersion()!=expected) throw stale();
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
        lock(op.getRemoteKey()); var g=em.find(RepoExecutionGuardEntity.class,op.getRemoteKey(),LockModeType.PESSIMISTIC_WRITE);
        if (g==null || !op.getSessionId().equals(g.getSessionId()) || op.getGeneration()!=g.getGeneration()
                || !op.getId().equals(g.getOperationId())) throw stale();
        return g;
    }
    private GitOperationEntity operationForUpdate(String id) {
        var op=em.find(GitOperationEntity.class,id);
        if(op==null) throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND,id);
        lock(op.getRemoteKey()); em.refresh(op,LockModeType.PESSIMISTIC_WRITE); return op;
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
    private BusinessException blocked(){return BusinessException.of(ErrorCode.GIT_WRITE_BLOCKED);}
    private BusinessException stale(){return BusinessException.of(ErrorCode.GIT_OPERATION_STALE);}
}
