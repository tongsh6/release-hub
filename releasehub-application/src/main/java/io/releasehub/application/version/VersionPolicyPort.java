package io.releasehub.application.version;

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
    void deleteById(VersionPolicyId id);
}
