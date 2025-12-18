package io.releasehub.infrastructure.persistence.repo;

import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Adapter：基础设施层对 Port 的实现
 */
@Repository
public class CodeRepositoryPersistenceAdapter implements CodeRepositoryPort {

    private final Map<RepoId, CodeRepository> store = new ConcurrentHashMap<>();

    @Override
    public void save(CodeRepository repository) {
        store.put(repository.getId(), repository);
    }

    @Override
    public Optional<CodeRepository> findById(RepoId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CodeRepository> findByProjectId(ProjectId projectId) {
        return store.values().stream()
                .filter(repo -> repo.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }
}
