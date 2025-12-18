package io.releasehub.application.repo;

import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;

import java.util.List;
import java.util.Optional;

/**
 * Port/Gateway：用例层对外部能力的抽象
 */
public interface CodeRepositoryPort {
    void save(CodeRepository repository);
    Optional<CodeRepository> findById(RepoId id);
    List<CodeRepository> findByProjectId(ProjectId projectId);
}
