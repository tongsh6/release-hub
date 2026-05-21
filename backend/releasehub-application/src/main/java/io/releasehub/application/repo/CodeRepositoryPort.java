package io.releasehub.application.repo;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    default PageResult<CodeRepository> searchPaged(String keyword, Set<String> groupCodes, int page, int size) {
        throw new UnsupportedOperationException("group scoped repository search is not implemented");
    }

    /**
     * 更新仓库的初始版本号
     */
    void updateInitialVersion(String repoId, String initialVersion, String versionSource);

    /**
     * 获取仓库的初始版本号
     */
    Optional<String> getInitialVersion(String repoId);

    /**
     * 获取仓库初始版本来源。
     */
    default Optional<String> getInitialVersionSource(String repoId) {
        return Optional.empty();
    }
}
