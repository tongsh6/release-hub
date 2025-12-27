package io.releasehub.infrastructure.persistence.repo;

import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter：基础设施层对 Port 的实现
 */
@Repository
@Primary
@RequiredArgsConstructor
public class CodeRepositoryPersistenceAdapter implements CodeRepositoryPort {

    private final CodeRepositoryJpaRepository repository;

    @Override
    public void save(CodeRepository domain) {
        CodeRepositoryJpaEntity entity = new CodeRepositoryJpaEntity(
                domain.getId().value(),
                domain.getProjectId().value(),
                domain.getGitlabProjectId(),
                domain.getName(),
                domain.getCloneUrl(),
                domain.getDefaultBranch(),
                domain.isMonoRepo(),
                domain.getBranchCount(),
                domain.getActiveBranchCount(),
                domain.getNonCompliantBranchCount(),
                domain.getMrCount(),
                domain.getOpenMrCount(),
                domain.getMergedMrCount(),
                domain.getClosedMrCount(),
                domain.getLastSyncAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getVersion()
        );
        repository.save(entity);
    }

    @Override
    public Optional<CodeRepository> findById(RepoId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public List<CodeRepository> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeRepository> findByProjectId(ProjectId projectId) {
        return repository.findByProjectId(projectId.value()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private CodeRepository toDomain(CodeRepositoryJpaEntity entity) {
        return CodeRepository.rehydrate(
                new RepoId(entity.getId()),
                new ProjectId(entity.getProjectId()),
                entity.getGitlabProjectId(),
                entity.getName(),
                entity.getCloneUrl(),
                entity.getDefaultBranch(),
                entity.isMonoRepo(),
                entity.getBranchCount(),
                entity.getActiveBranchCount(),
                entity.getNonCompliantBranchCount(),
                entity.getMrCount(),
                entity.getOpenMrCount(),
                entity.getMergedMrCount(),
                entity.getClosedMrCount(),
                entity.getLastSyncAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
