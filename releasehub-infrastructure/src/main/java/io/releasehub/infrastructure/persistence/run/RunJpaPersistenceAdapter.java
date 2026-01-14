package io.releasehub.infrastructure.persistence.run;

import io.releasehub.application.run.RunPort;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class RunJpaPersistenceAdapter implements RunPort {

    private final RunJpaRepository repository;

    @Override
    public void save(Run run) {
        RunJpaEntity entity = toEntity(run);
        repository.save(entity);
    }

    @Override
    public Optional<Run> findById(String runId) {
        return repository.findById(runId).map(this::toDomain);
    }

    @Override
    public List<Run> findAll() {
        return repository.findAll().stream()
                         .map(this::toDomain)
                         .collect(Collectors.toList());
    }

    @Override
    public PageResult<Run> findPaged(String runType, String operator, String windowKey, String repoId, String iterationKey, String status, int page, int size) {
        String normalizedRunType = normalize(runType);
        String normalizedOperator = normalize(operator);
        String normalizedWindowKey = normalize(windowKey);
        String normalizedRepoId = normalize(repoId);
        String normalizedIterationKey = normalize(iterationKey);
        String normalizedStatus = normalize(status);

        if (normalizedStatus != null) {
            List<Run> filtered = repository.findAllByFilters(
                                                   normalizedRunType,
                                                   normalizedOperator,
                                                   normalizedWindowKey,
                                                   normalizedRepoId,
                                                   normalizedIterationKey)
                                           .stream()
                                           .map(this::toDomain)
                                           .filter(run -> determineStatus(run).equalsIgnoreCase(normalizedStatus))
                                           .collect(Collectors.toList());
            int pageIndex = Math.max(page - 1, 0);
            int from = Math.min(pageIndex * size, filtered.size());
            int to = Math.min(from + size, filtered.size());
            List<Run> slice = filtered.subList(from, to);
            return new PageResult<>(slice, filtered.size());
        }

        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<RunJpaEntity> result = repository.findPagedByFilters(
                normalizedRunType,
                normalizedOperator,
                normalizedWindowKey,
                normalizedRepoId,
                normalizedIterationKey,
                pageable
        );
        List<Run> items = result.getContent().stream()
                                .map(this::toDomain)
                                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String determineStatus(Run run) {
        if (run.getItems().isEmpty()) {
            return run.getFinishedAt() != null ? "COMPLETED" : "RUNNING";
        }
        boolean hasFailed = run.getItems().stream()
                               .anyMatch(item -> item.getFinalResult() != null &&
                                       (item.getFinalResult().name().contains("FAILED") ||
                                               item.getFinalResult().name().equals("MERGE_BLOCKED")));
        if (hasFailed) {
            return "FAILED";
        }
        boolean allSuccess = run.getItems().stream()
                                .allMatch(item -> item.getFinalResult() != null &&
                                        item.getFinalResult().name().contains("SUCCESS"));
        if (allSuccess) {
            return "SUCCESS";
        }
        return run.getFinishedAt() != null ? "COMPLETED" : "RUNNING";
    }

    private Run toDomain(RunJpaEntity entity) {
        List<RunItem> items = entity.getItems().stream()
                                    .map(this::toDomainItem)
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
                entity.getVersion()
        );
    }

    private RunItem toDomainItem(RunItemJpaEntity entity) {
        List<RunStep> steps = entity.getSteps().stream()
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
                IterationKey.of(entity.getIterationKey()),
                entity.getPlannedOrder(),
                entity.getExecutedOrder(),
                entity.getFinalResult() != null ? RunItemResult.valueOf(entity.getFinalResult()) : null,
                steps,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                0L // RunItemJpaEntity doesn't have version field, defaulting to 0
        );
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

        List<RunItemJpaEntity> itemEntities = domain.getItems().stream()
                                                    .map(item -> {
                                                        RunItemJpaEntity itemEntity = new RunItemJpaEntity();
                                                        itemEntity.setId(item.getId().value());
                                                        itemEntity.setRun(entity); // Set parent reference
                                                        itemEntity.setWindowKey(item.getWindowKey());
                                                        itemEntity.setRepoId(item.getRepo().value());
                                                        itemEntity.setIterationKey(item.getIterationKey().value());
                                                        itemEntity.setPlannedOrder(item.getPlannedOrder());
                                                        itemEntity.setExecutedOrder(item.getExecutedOrder());
                                                        if (item.getFinalResult() != null) {
                                                            itemEntity.setFinalResult(item.getFinalResult().name());
                                                        }
                                                        itemEntity.setCreatedAt(item.getCreatedAt());
                                                        itemEntity.setUpdatedAt(item.getUpdatedAt());

                                                        List<RunStepJpaEmbeddable> steps = item.getSteps().stream()
                                                                                               .map(step -> new RunStepJpaEmbeddable(
                                                                                                       step.actionType().name(),
                                                                                                       step.result().name(),
                                                                                                       step.startAt(),
                                                                                                       step.endAt(),
                                                                                                       step.message()
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
