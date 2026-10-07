package io.releasehub.infrastructure.persistence.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.run.plan.RunPlanPort;
import io.releasehub.application.run.plan.RunPlanSummary;
import io.releasehub.application.run.plan.SavedRunPlan;
import io.releasehub.domain.run.RunPlanSnapshot;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RunPlanPersistenceAdapter implements RunPlanPort {
    private final JdbcTemplate jdbc;
    private final RunPlanJpaRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper json;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRequest(String operator, String key) {
        // 哈希冲突只增加串行等待，实际去重仍由完整主体/key 唯一约束和请求指纹判定。
        String scope = operator.length() + ":" + operator + ":" + key;
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", ps -> ps.setString(1, scope), (RowCallbackHandler) row -> {});
    }

    @Override
    public Optional<SavedRunPlan> findByRequest(String operator, String key) {
        return repository.findByOperatorAndIdempotencyKey(operator, key).map(this::map);
    }

    @Override
    public Optional<SavedRunPlan> findByRunId(String id) {
        return repository.findById(id).map(this::map);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(SavedRunPlan plan) {
        String snapshot;
        try {
            snapshot = json.writeValueAsString(plan.snapshot());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("计划快照序列化失败", e);
        }
        var entity = new RunPlanJpaEntity();
        entity.setRun(entityManager.getReference(RunJpaEntity.class, plan.runId()));
        entity.setWindowId(plan.windowId());
        entity.setOperator(plan.operator());
        entity.setIdempotencyKey(plan.idempotencyKey());
        entity.setRequestHash(plan.requestHash());
        entity.setPlanHash(plan.planHash());
        entity.setCreatedAt(plan.createdAt());
        entity.setItemCount(plan.snapshot().items().size());
        entity.setSnapshot(snapshot);
        // 快照仅新增：MapsId 从关联 Run 取标识，禁止 merge 覆盖已有快照。
        entityManager.persist(entity);
        entityManager.flush();
    }

    @Override
    public List<RunPlanSummary> listByWindow(String windowId, int limit) {
        return repository.listSummaries(windowId, PageRequest.of(0, Math.min(Math.max(limit, 1), 50)));
    }

    private SavedRunPlan map(RunPlanJpaEntity row) {
        try {
            return new SavedRunPlan(row.getRunId(), row.getWindowId(), row.getOperator(), row.getIdempotencyKey(),
                    row.getRequestHash(), row.getPlanHash(), row.getCreatedAt(), json.readValue(row.getSnapshot(), RunPlanSnapshot.class));
        } catch (JsonProcessingException e) {
            throw new DataRetrievalFailureException("计划快照无法读取", e);
        }
    }
}
