package io.releasehub.application.repo;

import io.releasehub.common.paging.PageResult;
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

    List<CodeRepository> findAll();

    void deleteById(RepoId id);

    List<CodeRepository> search(String keyword);

    PageResult<CodeRepository> searchPaged(String keyword, int page, int size);

    /**
     * 更新仓库的初始版本号
     */
    void updateInitialVersion(String repoId, String initialVersion, String versionSource);

    /**
     * 获取仓库的初始版本号
     */
    Optional<String> getInitialVersion(String repoId);
}
