package io.releasehub.infrastructure.persistence.run;

import io.releasehub.application.run.RunTaskPort;
import io.releasehub.domain.run.RunId;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskId;
import io.releasehub.domain.run.RunTaskStatus;
import io.releasehub.domain.run.RunTaskType;
import io.releasehub.domain.run.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RunTaskPersistenceAdapter implements RunTaskPort {

    private final RunTaskJpaRepository jpaRepository;

    @Override
    public void save(RunTask task) {
        RunTaskJpaEntity entity = new RunTaskJpaEntity(
                task.getId().value(),
                task.getRunId().value(),
                task.getTaskType().name(),
                task.getTaskOrder(),
                task.getTargetType() != null ? task.getTargetType().name() : null,
                task.getTargetId(),
                task.getStatus().name(),
                task.getRetryCount(),
                task.getMaxRetries(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt()
        );
        jpaRepository.save(entity);
    }

    @Override
    public Optional<RunTask> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<RunTask> findByRunId(String runId) {
        return jpaRepository.findByRunIdOrderByTaskOrder(runId).stream()
                            .map(this::toDomain)
                            .collect(Collectors.toList());
    }

    @Override
    public List<RunTask> findByRunIdAndStatus(String runId, String status) {
        return jpaRepository.findByRunIdAndStatus(runId, status).stream()
                            .map(this::toDomain)
                            .collect(Collectors.toList());
    }

    private RunTask toDomain(RunTaskJpaEntity e) {
        return RunTask.rehydrate(
                RunTaskId.of(e.getId()),
                RunId.of(e.getRunId()),
                RunTaskType.valueOf(e.getTaskType()),
                e.getTaskOrder(),
                e.getTargetType() != null ? TargetType.valueOf(e.getTargetType()) : null,
                e.getTargetId(),
                RunTaskStatus.valueOf(e.getStatus()),
                e.getRetryCount(),
                e.getMaxRetries(),
                e.getErrorMessage(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getCreatedAt(),
                e.getCreatedAt()  // 使用 createdAt 作为 updatedAt
        );
    }
}
