package io.releasehub.infrastructure.persistence.project;

import io.releasehub.application.project.ProjectPort;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.project.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
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
    public void deleteById(ProjectId id) {
        repository.deleteById(id.value());
    }

    private Project toDomain(ProjectJpaEntity entity) {
        return Project.rehydrate(
                new ProjectId(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                ProjectStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
