package io.releasehub.infrastructure.persistence.run;

import io.releasehub.application.run.RunPort;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunId;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemId;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
public class RunJpaPersistenceAdapter implements RunPort {

    private static final Set<String> SUCCESSFUL_TASKS = Set.of("COMPLETED", "INHERITED");
    private static final Set<String> MR_IDENTITY = Set.of("git.projectId", "git.sourceBranch", "git.targetBranch",
            "git.mrIid", "git.mrUrl", "git.sourceSha");
    private static final Set<String> MR_RESULT = Set.of("git.mergeSha", "git.mrDisposition", "git.blockReason", "git.requestId");
    private static final String NO_TASK_EVIDENCE = """
            NOT EXISTS (SELECT 1 FROM {h-schema}git_operation o WHERE o.task_id=t.id)
            AND NOT EXISTS (SELECT 1 FROM {h-schema}run_step s WHERE s.task_id=t.id)
            """;

    private final RunJpaRepository repository;
    private final RunTaskJpaRepository tasks;
    private final EntityManager entityManager;
    private final Clock clock;
    private final WindowIterationPort bindings;
    private final GitOperationJournalPort journal;
    private final long claimLeaseMillis;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "EntityManager 是事务绑定的 Spring 共享代理，仅内部使用；非法期限在任何字段赋值前拒绝，"
                    + "构造过程不分配资源、不发布自身，类需保留 Spring 事务代理能力")
    public RunJpaPersistenceAdapter(RunJpaRepository repository, RunTaskJpaRepository tasks, EntityManager entityManager,
                                    Clock clock, WindowIterationPort bindings, GitOperationJournalPort journal,
                                    @Value("${releasehub.run.worker.claim-lease-ms:3600000}") long claimLeaseMillis) {
        if (claimLeaseMillis <= 0) throw new IllegalArgumentException("releasehub.run.worker.claim-lease-ms must be positive");
        this.repository = repository;
        this.tasks = tasks;
        this.entityManager = entityManager;
        this.clock = clock;
        this.bindings = bindings;
        this.journal = journal;
        this.claimLeaseMillis = claimLeaseMillis;
    }

    @Override
    @Transactional
    public void save(Run run) {
        // 新协议仅在受理时插入聚合，之后的投影必须经 task CAS 提交。
        if (repository.findForUpdate(run.getId().value()).isPresent() && tasks.existsByRunId(run.getId().value())) {
            throw stale();
        }
        RunJpaEntity entity = toEntity(run);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Run> findById(String runId) {
        return repository.findById(runId).map(entity -> toDomains(List.of(entity)).getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Run> findAll() {
        return toDomains(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Run> findByWindowKey(String windowKey) {
        String normalizedWindowKey = normalize(windowKey);
        if (normalizedWindowKey == null) {
            return List.of();
        }
        return toDomains(repository.findByWindowKey(normalizedWindowKey));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Run> findPaged(String runType, String operator, String windowKey, String repoId, String iterationKey, String status, String groupCode, int page, int size) {
        String normalizedRunType = normalize(runType);
        String normalizedOperator = normalize(operator);
        String normalizedWindowKey = normalize(windowKey);
        String normalizedRepoId = normalize(repoId);
        String normalizedIterationKey = normalize(iterationKey);
        String normalizedStatus = normalize(status);
        String normalizedGroupCode = normalize(groupCode);

        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<RunJpaEntity> result = repository.findPagedByFilters(
                normalizedRunType,
                normalizedOperator,
                normalizedWindowKey,
                normalizedRepoId,
                normalizedIterationKey,
                normalizedStatus,
                normalizedGroupCode,
                pageable
        );
        List<Run> items = toDomains(result.getContent());
        return new PageResult<>(items, result.getTotalElements());
    }


    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createTasks(Run run) {
        freshPersistenceContext();
        RunJpaEntity stored = lockedRun(run.getId().value());
        requireTaskRun(stored);
        if (tasks.existsByRunId(stored.getId()) || stored.getItems().isEmpty()
                || !stored.getItems().stream().map(RunItemJpaEntity::getId).collect(Collectors.toSet())
                .equals(run.getItems().stream().map(item -> item.getId().value()).collect(Collectors.toSet()))) throw stale();
        var ordered = stored.getItems().stream().sorted(Comparator.comparingInt(RunItemJpaEntity::getPlannedOrder)).toList();
        List<RunTaskJpaEntity> intents = new ArrayList<>();
        int order = 0;
        for (RunItemJpaEntity item : ordered) {
            if (item.getIterationKey() == null || item.getFinalResult() != null || !item.getSteps().isEmpty()) throw stale();
            Set<ActionType> inherited = inheritedPrefix(item);
            for (ActionType action : ActionType.orchestrationSequence()) {
                var task = new RunTaskJpaEntity();
                task.setId(UUID.randomUUID().toString());
                task.setRunId(stored.getId());
                task.setTaskType(action.name());
                task.setTaskOrder(++order);
                task.setTargetType("RUN_ITEM");
                task.setTargetId(item.getId());
                task.setStatus(inherited.contains(action) ? "INHERITED" : "PENDING");
                task.setCreatedAt(now());
                intents.add(task);
            }
        }
        tasks.saveAll(intents);
        entityManager.flush();
    }

    private Set<ActionType> inheritedPrefix(RunItemJpaEntity item) {
        String sourceRun = item.getMetadata().get("retry.sourceRunId");
        String sourceItem = item.getMetadata().get("retry.sourceItemId");
        if (sourceRun == null && sourceItem == null) return Set.of();
        if (sourceRun == null || sourceItem == null) throw stale();
        RunJpaEntity previous = repository.findById(sourceRun).orElseThrow(this::stale);
        requireTaskRun(previous);
        var original = previous.getItems().stream().filter(value -> value.getId().equals(sourceItem))
                .findFirst().orElseThrow(this::stale);
        if (!Objects.equals(original.getRepoId(), item.getRepoId())
                || !Objects.equals(original.getWindowKey(), item.getWindowKey())
                || !Objects.equals(original.getIterationKey(), item.getIterationKey())
                || original.getFinalResult() == null
                || !Set.of("FAILED", "MERGE_BLOCKED", "SKIPPED_DUE_TO_BLOCK").contains(original.getFinalResult())) throw stale();
        var source = tasks.findByRunIdOrderByTaskOrderAsc(sourceRun).stream()
                .filter(task -> task.getTargetId().equals(sourceItem)).toList();
        if (source.size() != 4) throw stale();
        Set<ActionType> inherited = new HashSet<>();
        boolean stopped = false;
        for (int index = 0; index < source.size(); index++) {
            var task = source.get(index);
            ActionType action = ActionType.orchestrationSequence().get(index);
            if (!action.name().equals(task.getTaskType()) || Set.of("RUNNING", "UNKNOWN").contains(task.getStatus())) throw stale();
            if (SUCCESSFUL_TASKS.contains(task.getStatus())) {
                if (stopped) throw stale();
                inherited.add(action);
            } else {
                stopped = true;
            }
        }
        if (!stopped) throw stale();
        return inherited;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findTaskCandidateRunIds(String afterId, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Scan limit must be between 1 and 1000");
        List<?> candidates = entityManager.createNativeQuery("""
                SELECT r.id FROM {h-schema}run r
                WHERE r.run_type=?1 AND r.execution_version=?2
                  AND r.execution_status IN ('RUNNING', 'NEEDS_REVIEW') AND r.id>?3
                  AND EXISTS (
                    SELECT 1 FROM {h-schema}run_task t WHERE t.run_id=r.id
                      AND (t.status='PENDING' OR (
                        t.status='RUNNING' AND t.claim_token IS NOT NULL AND t.started_at IS NOT NULL
                        AND t.claim_expires_at IS NOT NULL AND t.claim_expires_at<=clock_timestamp()
                        AND ((
                """ + NO_TASK_EVIDENCE + """
                        ) OR (
                          t.task_type='TRY_MERGE'
                          AND NOT EXISTS (SELECT 1 FROM {h-schema}run_step s WHERE s.task_id=t.id)
                          AND EXISTS (SELECT 1 FROM {h-schema}git_operation o WHERE o.task_id=t.id
                            AND o.action='MERGE_MR' AND o.state IN ('APPLIED', 'REJECTED', 'INTENT_RECORDED'))
                        ))
                        )))
                ORDER BY r.id
                """)
                .setParameter(1, RunType.WINDOW_ORCHESTRATION.name())
                .setParameter(2, Run.ORCHESTRATION_PROTOCOL_VERSION)
                .setParameter(3, afterId == null ? "" : afterId)
                .setMaxResults(limit)
                .getResultList();
        return candidates.stream().map(String.class::cast).toList();
    }

    @Override
    @Transactional
    public boolean projectNextConfirmedTask(String runId) {
        freshPersistenceContext();
        RunJpaEntity run = lockedRun(runId);
        requireTaskRun(run);
        if (!Set.of("RUNNING", "NEEDS_REVIEW").contains(run.getExecutionStatus())) return false;
        var ordered = tasks.findByRunIdOrderByTaskOrderAsc(runId);
        Map<String, RunItemJpaEntity> items = taskItems(run, ordered);
        for (var candidate : ordered) {
            if (!"RUNNING".equals(candidate.getStatus())
                    || !Set.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE").contains(candidate.getTaskType())) continue;
            RunItemJpaEntity item = items.get(candidate.getTargetId());
            if (item.getExecutedOrder() <= 0 || !eligible(candidate, item, ordered, items)) continue;
            RunTaskJpaEntity task = tasks.findForUpdate(candidate.getId()).orElseThrow(this::stale);
            if (!expiredRunningClaim(task, databaseNow()) || task.getClaimToken().isBlank()
                    || task.getFinishedAt() != null) continue;
            Number steps = (Number) entityManager.createNativeQuery(
                    "SELECT count(*) FROM {h-schema}run_step WHERE task_id=?1")
                    .setParameter(1, task.getId()).getSingleResult();
            if (steps.longValue() != 0) continue;
            var claim = new TaskClaim(new TaskFence(task.getId(), task.getClaimToken()), runId, item.getId(),
                    RepoId.of(item.getRepoId()), ActionType.valueOf(task.getTaskType()), now(), run.getOperator(),
                    taskMetadata(run, item), null);
            Map<String, String> updates;
            String message;
            RunItemResult result = RunItemResult.CREATED;
            if (claim.actionType() == ActionType.ENSURE_MR) {
                var evidence = journal.confirmedAppliedCreatedMr(claim);
                if (evidence.isEmpty()) continue;
                updates = RunPort.mrEvidenceUpdates(evidence.get(), null);
                message = "根据已确认的 MR 创建结果恢复检查点";
            } else if (claim.actionType() == ActionType.TRY_MERGE) {
                var confirmed = journal.confirmedMergeResult(claim);
                if (confirmed.isEmpty()) continue;
                var merge = confirmed.get();
                updates = RunPort.mrResultUpdates(merge);
                result = RunPort.mergeOutcome(merge);
                message = merge.detail() == null ? "根据已确认的 MR 合并结果恢复记录" : merge.detail();
            } else {
                var project = journal.confirmedAppliedBranchProject(claim);
                if (project.isEmpty()) continue;
                updates = Map.of("git.projectId", project.get());
                message = "根据已确认的分支创建结果恢复记录";
            }
            // 外层代理已建立结果事务；自调用沿同一物理事务提交，clear 后不再使用原实体。
            completeTask(claim, new RunStep(claim.actionType(), result,
                    claim.startedAt(), now(), message), updates);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Optional<TaskClaim> claimNextTask(String runId) {
        freshPersistenceContext();
        RunJpaEntity run = lockedRun(runId);
        requireTaskRun(run);
        if (!Set.of("RUNNING", "NEEDS_REVIEW").contains(run.getExecutionStatus())) return Optional.empty();
        var ordered = tasks.findByRunIdOrderByTaskOrderAsc(runId);
        Map<String, RunItemJpaEntity> items = taskItems(run, ordered);
        for (var candidate : ordered) {
            if (!Set.of("PENDING", "RUNNING").contains(candidate.getStatus())) continue;
            RunItemJpaEntity item = items.get(candidate.getTargetId());
            if (!eligible(candidate, item, ordered, items)) continue;
            // 所有领取、结果和写意图都遵循 Run → Task 锁序；拿锁后才采样数据库时间。
            RunTaskJpaEntity task = tasks.findForUpdate(candidate.getId()).orElseThrow(this::stale);
            Instant databaseTime = databaseNow();
            boolean initialClaim = "PENDING".equals(task.getStatus());
            if (initialClaim) {
                if (task.getClaimToken() != null) throw stale();
            } else if (item.getExecutedOrder() <= 0 || !expiredRunningClaim(task, databaseTime)) {
                continue;
            }
            Map<String, String> metadata = taskMetadata(run, item);
            GitOperationJournalPort.Operation unsentOperation = null;
            if (!initialClaim && !recoverableWithoutEvidence(task, databaseTime)) {
                if (!Set.of("TRY_MERGE", "ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR").contains(task.getTaskType())) continue;
                Number steps = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM {h-schema}run_step WHERE task_id=?1")
                        .setParameter(1, task.getId()).getSingleResult();
                if (steps.longValue() != 0) continue;
                var currentClaim = new TaskClaim(new TaskFence(task.getId(), task.getClaimToken()), runId, item.getId(),
                        RepoId.of(item.getRepoId()), ActionType.valueOf(task.getTaskType()), now(), run.getOperator(), metadata, null);
                var prepared = journal.prepareUnsentTaskClaim(currentClaim);
                if (prepared.isEmpty()) continue;
                unsentOperation = prepared.get();
            }
            Instant started = now();
            String token = UUID.randomUUID().toString();
            OffsetDateTime expiresAt = databaseTime.plusMillis(claimLeaseMillis).atOffset(ZoneOffset.UTC);
            if (initialClaim) {
                requireOne(update("UPDATE {h-schema}run_task SET status='RUNNING', claim_token=?1, started_at=?2, claim_expires_at=?3 "
                        + "WHERE id=?4 AND status='PENDING' AND claim_token IS NULL",
                        token, stamp(started), expiresAt, task.getId()));
            } else {
                // 原意图已在同一物理事务中锁定并验证；CAS 失败必须回滚其观察版本变更。
                String evidenceFence = unsentOperation != null
                        ? "NOT EXISTS (SELECT 1 FROM {h-schema}run_step s WHERE s.task_id=t.id)"
                        : NO_TASK_EVIDENCE;
                requireOne(update("""
                        UPDATE {h-schema}run_task t SET claim_token=?1, claim_expires_at=?2
                        WHERE t.id=?3 AND t.status='RUNNING' AND t.claim_token=?4
                          AND t.started_at IS NOT NULL AND t.claim_expires_at=?5 AND t.claim_expires_at<=?6
                          AND
                        """ + evidenceFence,
                        token, expiresAt, task.getId(), task.getClaimToken(),
                        task.getClaimExpiresAt().atOffset(ZoneOffset.UTC), databaseTime.atOffset(ZoneOffset.UTC)));
            }
            if (initialClaim && item.getExecutedOrder() == 0) {
                int order = run.getItems().stream().mapToInt(RunItemJpaEntity::getExecutedOrder).max().orElse(0) + 1;
                requireOne(update("UPDATE {h-schema}run_item SET executed_order=?1, updated_at=?2 WHERE id=?3 AND executed_order=0",
                        order, stamp(started), item.getId()));
            }
            var claim = new TaskClaim(new TaskFence(task.getId(), token), runId, item.getId(),
                    RepoId.of(item.getRepoId()), ActionType.valueOf(task.getTaskType()), started, run.getOperator(), metadata, unsentOperation);
            entityManager.clear();
            return Optional.of(claim);
        }
        return Optional.empty();
    }

    private boolean expiredRunningClaim(RunTaskJpaEntity task, Instant databaseTime) {
        return "RUNNING".equals(task.getStatus()) && task.getClaimToken() != null && task.getStartedAt() != null
                && task.getClaimExpiresAt() != null && !task.getClaimExpiresAt().isAfter(databaseTime);
    }

    private boolean recoverableWithoutEvidence(RunTaskJpaEntity task, Instant databaseTime) {
        if (!expiredRunningClaim(task, databaseTime)) return false;
        Number matches = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM {h-schema}run_task t WHERE t.id=?1 AND " + NO_TASK_EVIDENCE)
                .setParameter(1, task.getId()).getSingleResult();
        return matches.longValue() == 1;
    }

    private Instant databaseNow() {
        return ((OffsetDateTime) entityManager.createNativeQuery("SELECT clock_timestamp()", OffsetDateTime.class)
                .getSingleResult()).toInstant();
    }

    private boolean eligible(RunTaskJpaEntity candidate, RunItemJpaEntity item, List<RunTaskJpaEntity> ordered,
                             Map<String, RunItemJpaEntity> items) {
        boolean failFast = failFast(item);
        for (var before : ordered) {
            if (before.getTaskOrder() >= candidate.getTaskOrder()) break;
            if (!item.getRepoId().equals(items.get(before.getTargetId()).getRepoId())) continue;
            if (Set.of("PENDING", "RUNNING", "UNKNOWN").contains(before.getStatus())) return false;
            if (before.getTargetId().equals(item.getId()) && !SUCCESSFUL_TASKS.contains(before.getStatus())) return false;
            if (failFast && Set.of("FAILED", "SKIPPED").contains(before.getStatus())) return false;
        }
        return true;
    }

    @Override
    @Transactional
    public Run completeTask(TaskClaim claim, RunStep step, Map<String, String> metadataUpdates) {
        if (claim == null || claim.fence() == null || claim.fence().taskId() == null || claim.fence().claimToken() == null
                || step == null || step.result() == null || step.startAt() == null || step.endAt() == null
                || step.endAt().isBefore(step.startAt()) || metadataUpdates == null) throw stale();
        freshPersistenceContext();
        RunJpaEntity run = lockedRun(claim.runId());
        requireTaskRun(run);
        RunTaskJpaEntity task = tasks.findForUpdate(claim.fence().taskId()).orElseThrow(this::stale);
        RunItemJpaEntity item = run.getItems().stream().filter(value -> value.getId().equals(task.getTargetId()))
                .findFirst().orElseThrow(this::stale);
        if (!"RUNNING".equals(task.getStatus()) || !claim.fence().claimToken().equals(task.getClaimToken())
                || !run.getId().equals(task.getRunId()) || !"RUN_ITEM".equals(task.getTargetType())
                || !item.getId().equals(claim.itemId()) || !RepoId.of(item.getRepoId()).equals(claim.repo())
                || !run.getOperator().equals(claim.operator()) || claim.actionType() == null
                || !task.getTaskType().equals(claim.actionType().name())
                || step.actionType() != claim.actionType() || !taskMetadata(run, item).equals(claim.metadata())) throw stale();
        validateResult(step);
        Map<String, String> patch = validatedPatch(run, item, claim.actionType(), metadataUpdates);
        var ordered = tasks.findByRunIdOrderByTaskOrderAsc(run.getId());
        Map<String, RunItemJpaEntity> items = taskItems(run, ordered);
        Map<String, RunTaskJpaEntity> taskIndex = ordered.stream()
                .collect(Collectors.toMap(RunTaskJpaEntity::getId, value -> value));
        Instant finished = now();

        // 在原检查点与 Step 改写前核准持久结果；后续 Task CAS 失败时，本事务的结果及操作变更一起回滚。
        RunItemResult result = journal.acknowledgeTask(claim.fence(), step.result(), patch);
        requireOne(update("INSERT INTO {h-schema}run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                + "VALUES (?1,?2,?3,?4,?5,?6,?7)", item.getId(), step.actionType().name(), result.name(),
                stamp(step.startAt()), stamp(step.endAt()), step.message(), task.getId()));
        persistMetadata(item.getId(), item.getMetadata(), patch);
        if (result == RunItemResult.UNKNOWN || result.isFailure()) {
            requireOne(update("UPDATE {h-schema}run_item SET final_result=?1,updated_at=?2 WHERE id=?3",
                    result.name(), stamp(finished), item.getId()));
            skipAfterFailure(task, item, ordered, items, result, finished);
        } else if (step.actionType() == ActionType.TRY_MERGE) {
            requireOne(update("UPDATE {h-schema}run_item SET final_result=?1,updated_at=?2 WHERE id=?3",
                    result.name(), stamp(finished), item.getId()));
        } else {
            requireOne(update("UPDATE {h-schema}run_item SET updated_at=?1 WHERE id=?2", stamp(finished), item.getId()));
        }
        if (!result.isFailure() && result != RunItemResult.UNKNOWN) {
            String window = item.getMetadata().get("plan.windowId");
            if (step.actionType() == ActionType.ENSURE_RELEASE) {
                bindings.updateReleaseBranch(window, item.getIterationKey(), item.getMetadata().get("plan.releaseBranch"), finished);
            } else if (step.actionType() == ActionType.TRY_MERGE) {
                bindings.updateLastMergeAt(window, item.getIterationKey(), finished);
            }
        }
        String status = result == RunItemResult.UNKNOWN ? "UNKNOWN"
                : result.isFailure() ? "FAILED" : "COMPLETED";
        requireOne(update("UPDATE {h-schema}run_task SET status=?1,finished_at=?2,error_message=?3 "
                + "WHERE id=?4 AND status='RUNNING' AND claim_token=?5", status, stamp(finished),
                "COMPLETED".equals(status) ? null : step.message(), task.getId(), claim.fence().claimToken()));
        entityManager.flush();
        entityManager.clear();
        Run restored = toDomain(repository.findById(run.getId()).orElseThrow(this::stale), taskIndex);
        boolean pending = tasks.findByRunIdOrderByTaskOrderAsc(run.getId()).stream()
                .anyMatch(value -> Set.of("PENDING", "RUNNING").contains(value.getStatus()));
        restored.refreshTaskProgress(pending, finished);
        requireOne(update("UPDATE {h-schema}run SET execution_status=?1,finished_at=?2,updated_at=?3,version=version+1 WHERE id=?4",
                restored.getExecutionStatus().name(), stamp(restored.getFinishedAt()), stamp(finished), run.getId()));
        entityManager.clear();
        return toDomain(repository.findById(run.getId()).orElseThrow(this::stale), taskIndex);
    }

    private void validateResult(RunStep step) {
        boolean allowed = switch (step.actionType()) {
            case ENSURE_FEATURE, ENSURE_RELEASE -> Set.of(RunItemResult.CREATED, RunItemResult.ALREADY_EXISTS,
                    RunItemResult.FAILED, RunItemResult.UNKNOWN).contains(step.result());
            case ENSURE_MR -> Set.of(RunItemResult.CREATED, RunItemResult.MR_REUSED, RunItemResult.ALREADY_MERGED,
                    RunItemResult.MERGE_BLOCKED, RunItemResult.FAILED, RunItemResult.UNKNOWN).contains(step.result());
            case TRY_MERGE -> Set.of(RunItemResult.MERGED, RunItemResult.ALREADY_MERGED,
                    RunItemResult.MERGE_BLOCKED, RunItemResult.FAILED, RunItemResult.UNKNOWN).contains(step.result());
            default -> false;
        };
        if (!allowed) throw stale();
    }

    private Map<String, String> validatedPatch(RunJpaEntity run, RunItemJpaEntity item, ActionType action,
                                               Map<String, String> updates) {
        Map<String, String> patch = new LinkedHashMap<>();
        String frozenProject = repositoryProjectId(run, item.getRepoId());
        for (var entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) throw stale();
            boolean allowed = (action == ActionType.ENSURE_MR || action == ActionType.TRY_MERGE)
                    && (MR_IDENTITY.contains(key) || MR_RESULT.contains(key))
                    || (action == ActionType.ENSURE_FEATURE || action == ActionType.ENSURE_RELEASE)
                    && Set.of("git.projectId", "git.blockReason", "git.requestId").contains(key);
            if (!allowed) throw stale();
            if ("git.projectId".equals(key) && (value == null || !value.matches("[1-9][0-9]*")
                    || frozenProject != null && !frozenProject.equals(value))) throw stale();
            if (action == ActionType.TRY_MERGE && MR_IDENTITY.contains(key)
                    && (value == null || !value.equals(item.getMetadata().get(key)))) throw stale();
            patch.put(key, value);
        }
        if (frozenProject != null && !item.getMetadata().containsKey("git.projectId")) patch.put("git.projectId", frozenProject);
        return patch;
    }

    private void skipAfterFailure(RunTaskJpaEntity current, RunItemJpaEntity failed, List<RunTaskJpaEntity> ordered,
                                  Map<String, RunItemJpaEntity> items, RunItemResult result, Instant finished) {
        boolean stopRepository = result == RunItemResult.UNKNOWN || failFast(failed);
        Set<String> skippedItems = new HashSet<>();
        String message = "同仓库前序执行项阻塞: " + failed.getId();
        for (var task : ordered) {
            if (task.getTaskOrder() <= current.getTaskOrder()) continue;
            RunItemJpaEntity item = items.get(task.getTargetId());
            if (!failed.getRepoId().equals(item.getRepoId())
                    || !failed.getId().equals(item.getId()) && !stopRepository) continue;
            if ("RUNNING".equals(task.getStatus())) throw stale();
            if (!"PENDING".equals(task.getStatus())) continue;
            requireOne(update("UPDATE {h-schema}run_task SET status='SKIPPED',finished_at=?1,error_message=?2 "
                    + "WHERE id=?3 AND status='PENDING' AND claim_token IS NULL", stamp(finished), message, task.getId()));
            if (!failed.getId().equals(item.getId())) skippedItems.add(item.getId());
        }
        for (String id : skippedItems) {
            requireOne(update("UPDATE {h-schema}run_item SET final_result='SKIPPED_DUE_TO_BLOCK',updated_at=?1 "
                    + "WHERE id=?2 AND final_result IS NULL", stamp(finished), id));
            persistMetadata(id, items.get(id).getMetadata(), Map.of("blockedBy.itemId", failed.getId()));
        }
    }

    private Map<String, RunItemJpaEntity> taskItems(RunJpaEntity run, List<RunTaskJpaEntity> ordered) {
        Map<String, RunItemJpaEntity> items = new HashMap<>();
        for (var item : run.getItems()) items.put(item.getId(), item);
        if (ordered.size() != items.size() * 4) throw stale();
        Map<String, Set<String>> actions = new HashMap<>();
        for (var task : ordered) {
            if (!run.getId().equals(task.getRunId()) || !"RUN_ITEM".equals(task.getTargetType()) || !items.containsKey(task.getTargetId())
                    || !ActionType.orchestrationSequence().stream().map(Enum::name).toList().contains(task.getTaskType())
                    || !actions.computeIfAbsent(task.getTargetId(), ignored -> new HashSet<>()).add(task.getTaskType())) throw stale();
        }
        return items;
    }

    private Map<String, String> taskMetadata(RunJpaEntity run, RunItemJpaEntity item) {
        Map<String, String> result = new LinkedHashMap<>(item.getMetadata());
        String project = repositoryProjectId(run, item.getRepoId());
        if (project != null) result.put("git.projectId", project);
        return result;
    }

    private String repositoryProjectId(RunJpaEntity run, String repoId) {
        try {
            // 本调用只汇总项目 metadata，不为未消费的 Step 顺序查询 Task。
            return toDomain(run, Map.of()).repositoryProjectId(RepoId.of(repoId)).orElse(null);
        } catch (IllegalStateException error) {
            throw stale();
        }
    }

    private boolean failFast(RunItemJpaEntity item) {
        String value = item.getMetadata().get("plan.failFast");
        if (value == null || !Set.of("true", "false").contains(value)) throw stale();
        return Boolean.parseBoolean(value);
    }

    private void persistMetadata(String itemId, Map<String, String> existing, Map<String, String> patch) {
        for (var entry : patch.entrySet()) {
            if (Objects.equals(existing.get(entry.getKey()), entry.getValue())) continue;
            update("DELETE FROM {h-schema}run_item_metadata WHERE run_item_id=?1 AND metadata_key=?2", itemId, entry.getKey());
            if (entry.getValue() != null) requireOne(update(
                    "INSERT INTO {h-schema}run_item_metadata(run_item_id,metadata_key,metadata_value) VALUES (?1,?2,?3)",
                    itemId, entry.getKey(), entry.getValue()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskView> findTasks(String runId) {
        return tasks.findByRunIdOrderByTaskOrderAsc(runId).stream().map(task -> new TaskView(task.getId(), task.getRunId(),
                task.getTargetId(), ActionType.valueOf(task.getTaskType()), task.getTaskOrder(), task.getStatus(),
                task.getStartedAt(), task.getFinishedAt(), task.getErrorMessage())).toList();
    }

    private RunJpaEntity lockedRun(String id) {
        return repository.findForUpdate(id).orElseThrow(() -> BusinessException.of(ErrorCode.RUN_NOT_FOUND, id));
    }

    private void requireTaskRun(RunJpaEntity run) {
        if (!RunType.WINDOW_ORCHESTRATION.name().equals(run.getRunType())
                || !Integer.valueOf(Run.ORCHESTRATION_PROTOCOL_VERSION).equals(run.getExecutionVersion())
                || "PLANNED".equals(run.getExecutionStatus())) throw stale();
    }

    private void freshPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private int update(String sql, Object... parameters) {
        var query = entityManager.createNativeQuery(sql);
        for (int index = 0; index < parameters.length; index++) query.setParameter(index + 1, parameters[index]);
        return query.executeUpdate();
    }

    private void requireOne(int affected) {
        if (affected != 1) throw stale();
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private Timestamp stamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private BusinessException stale() { return BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE); }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /** 本次读取的 Step 一次取得现有 Task；无关联 Step 时不增加查询。 */
    private List<Run> toDomains(List<RunJpaEntity> entities) {
        Set<String> taskIds = entities.stream()
                .flatMap(run -> run.getItems().stream())
                .flatMap(item -> item.getSteps().stream())
                .map(RunStepJpaEmbeddable::getTaskId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, RunTaskJpaEntity> taskIndex = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (var task : tasks.findAllById(taskIds)) taskIndex.put(task.getId(), task);
        }
        return entities.stream().map(entity -> toDomain(entity, taskIndex)).collect(Collectors.toList());
    }

    private Run toDomain(RunJpaEntity entity, Map<String, RunTaskJpaEntity> taskIndex) {
        List<RunItem> items = entity.getItems().stream()
                                    .map(item -> toDomainItem(entity.getId(), item, taskIndex))
                                    .collect(Collectors.toList());

        return Run.rehydrate(
                RunId.of(entity.getId()),
                RunType.valueOf(entity.getRunType()),
                entity.getOperator(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion(),
                entity.getExecutionVersion() == null ? 1 : entity.getExecutionVersion(),
                entity.getExecutionStatus() == null ? null : io.releasehub.domain.run.RunExecutionStatus.valueOf(entity.getExecutionStatus())
        );
    }

    private RunItem toDomainItem(String runId, RunItemJpaEntity entity, Map<String, RunTaskJpaEntity> taskIndex) {
        // 关联 Task 按原任务顺序展示；未关联事实排在其后，组间顺序不是因果证据。
        // 非 Task 只按现有时间展示；相同时间键保留所有行，不推造真实执行先后。
        Comparator<RunStepJpaEmbeddable> stepOrder = Comparator.comparing(
                (RunStepJpaEmbeddable step) -> taskOrderForStep(runId, entity.getId(), step, taskIndex),
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RunStepJpaEmbeddable::getStartAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RunStepJpaEmbeddable::getEndAt, Comparator.nullsLast(Comparator.naturalOrder()));
        List<RunStep> steps = entity.getSteps().stream()
                                    .sorted(stepOrder)
                                    .map(s -> new RunStep(
                                            ActionType.valueOf(s.getActionType()),
                                            RunItemResult.valueOf(s.getResult()),
                                            s.getStartAt(),
                                            s.getEndAt(),
                                            s.getMessage()
                                    ))
                                    .collect(Collectors.toList());

        return RunItem.rehydrate(
                RunItemId.of(entity.getId()),
                entity.getWindowKey(),
                RepoId.of(entity.getRepoId()),
                entity.getIterationKey() == null ? null : IterationKey.of(entity.getIterationKey()),
                entity.getPlannedOrder(),
                entity.getExecutedOrder(),
                entity.getFinalResult() != null ? RunItemResult.valueOf(entity.getFinalResult()) : null,
                steps,
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                0L // RunItemJpaEntity doesn't have version field, defaulting to 0
        );
    }

    private Integer taskOrderForStep(String runId, String itemId, RunStepJpaEmbeddable step,
                                     Map<String, RunTaskJpaEntity> taskIndex) {
        if (step.getTaskId() == null) return null;
        var task = taskIndex.get(step.getTaskId());
        if (task == null || !Objects.equals(runId, task.getRunId())
                || !"RUN_ITEM".equals(task.getTargetType()) || !Objects.equals(itemId, task.getTargetId())
                || !Objects.equals(step.getActionType(), task.getTaskType())) return null;
        return task.getTaskOrder();
    }

    private RunJpaEntity toEntity(Run domain) {
        RunJpaEntity entity = new RunJpaEntity();
        entity.setId(domain.getId().value());
        entity.setRunType(domain.getRunType().name());
        entity.setOperator(domain.getOperator());
        entity.setStartedAt(domain.getStartedAt());
        entity.setFinishedAt(domain.getFinishedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVersion(domain.getVersion());
        entity.setExecutionVersion(domain.getExecutionVersion());
        entity.setExecutionStatus(domain.getExecutionStatus() == null ? null : domain.getExecutionStatus().name());

        List<RunItemJpaEntity> itemEntities = domain.getItems().stream()
                                                    .map(item -> {
                                                        RunItemJpaEntity itemEntity = new RunItemJpaEntity();
                                                        itemEntity.setId(item.getId().value());
                                                        itemEntity.setRun(entity); // Set parent reference
                                                        itemEntity.setWindowKey(item.getWindowKey());
                                                        itemEntity.setRepoId(item.getRepo().value());
                                                        itemEntity.setIterationKey(item.getIterationKey() == null ? null : item.getIterationKey().value());
                                                        itemEntity.setPlannedOrder(item.getPlannedOrder());
                                                        itemEntity.setExecutedOrder(item.getExecutedOrder());
                                                        if (item.getFinalResult() != null) {
                                                            itemEntity.setFinalResult(item.getFinalResult().name());
                                                        }
                                                        itemEntity.setCreatedAt(item.getCreatedAt());
                                                        itemEntity.setUpdatedAt(item.getUpdatedAt());
                                                        itemEntity.setMetadata(new java.util.LinkedHashMap<>(item.getMetadata()));

                                                        List<RunStepJpaEmbeddable> steps = item.getSteps().stream()
                                                                                               .map(step -> new RunStepJpaEmbeddable(
                                                                                                       step.actionType().name(),
                                                                                                       step.result().name(),
                                                                                                       step.startAt(),
                                                                                                       step.endAt(),
                                                                                                       step.message(),
                                                                                                       null
                                                                                               ))
                                                                                               .collect(Collectors.toList());
                                                        itemEntity.setSteps(steps);
                                                        return itemEntity;
                                                    })
                                                    .collect(Collectors.toList());

        entity.setItems(itemEntities);
        return entity;
    }
}
