package io.releasehub.infrastructure.persistence.project;

import io.releasehub.application.project.ProjectPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.project.ProjectStatus;
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
public class ProjectJpaPersistenceAdapter implements ProjectPort {

    private final ProjectJpaRepository repository;

    @Override
    public void save(Project project) {
        ProjectJpaEntity entity = new ProjectJpaEntity(
                project.getId().value(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion()
        );
        repository.save(entity);
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public List<Project> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<Project> findPaged(String name, String status, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<ProjectJpaEntity> result;
        boolean hasName = name != null && !name.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasName && hasStatus) {
            result = repository.findByNameContainingIgnoreCaseAndStatus(name.trim(), status.trim(), pageable);
        } else if (hasName) {
            result = repository.findByNameContainingIgnoreCase(name.trim(), pageable);
        } else if (hasStatus) {
            result = repository.findByStatus(status.trim(), pageable);
        } else {
            result = repository.findAll(pageable);
        }
        List<Project> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    @Override
    public void deleteById(ProjectId id) {
        repository.deleteById(id.value());
    }

    private Project toDomain(ProjectJpaEntity entity) {
        return Project.rehydrate(
                ProjectId.of(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                ProjectStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
