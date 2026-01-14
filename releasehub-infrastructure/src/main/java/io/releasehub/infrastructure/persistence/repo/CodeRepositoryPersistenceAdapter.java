package io.releasehub.infrastructure.persistence.repo;

import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        // 先检查是否存在，以保留版本管理字段
        CodeRepositoryJpaEntity existing = repository.findById(domain.getId().value()).orElse(null);
        
        CodeRepositoryJpaEntity entity = new CodeRepositoryJpaEntity(
                domain.getId().value(),
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
                domain.getVersion(),
                existing != null ? existing.getInitialVersion() : null,
                existing != null ? existing.getVersionSource() : null
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
    public void deleteById(RepoId id) {
        repository.deleteById(id.value());
    }

    @Override
    public List<CodeRepository> search(String keyword) {
        String k = keyword != null ? keyword.toLowerCase() : null;
        return repository.findAll().stream()
                .filter(e -> {
                    if (k == null || k.isBlank()) return true;
                    return (e.getName() != null && e.getName().toLowerCase().contains(k))
                            || (e.getCloneUrl() != null && e.getCloneUrl().toLowerCase().contains(k));
                })
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<CodeRepository> searchPaged(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<CodeRepositoryJpaEntity> result;
        if (keyword == null || keyword.isBlank()) {
            result = repository.findAll(pageable);
        } else {
            String k = keyword.trim();
            result = repository.findByNameContainingIgnoreCaseOrCloneUrlContainingIgnoreCase(k, k, pageable);
        }
        List<CodeRepository> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    private CodeRepository toDomain(CodeRepositoryJpaEntity entity) {
        return CodeRepository.rehydrate(
                RepoId.of(entity.getId()),
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

    @Override
    public void updateInitialVersion(String repoId, String initialVersion, String versionSource) {
        repository.findById(repoId).ifPresent(entity -> {
            entity.setInitialVersion(initialVersion);
            entity.setVersionSource(versionSource);
            repository.save(entity);
        });
    }

    @Override
    public Optional<String> getInitialVersion(String repoId) {
        return repository.findById(repoId)
                .map(CodeRepositoryJpaEntity::getInitialVersion);
    }
}
