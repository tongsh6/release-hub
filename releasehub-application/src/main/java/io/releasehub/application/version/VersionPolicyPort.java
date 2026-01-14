package io.releasehub.application.version;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;

import java.util.List;
import java.util.Optional;

/**
 * VersionPolicy Port 接口
 */
public interface VersionPolicyPort {
    void save(VersionPolicy policy);
    Optional<VersionPolicy> findById(VersionPolicyId id);
    List<VersionPolicy> findAll();
    PageResult<VersionPolicy> findPaged(String keyword, int page, int size);
    void deleteById(VersionPolicyId id);
}
