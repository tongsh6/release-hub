package io.releasehub.infrastructure.persistence.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * VersionPolicy Port 的内存实现（临时实现，用于 MVP）
 * <p>
 * TODO: 后续需要实现 JPA 持久化
 */
@Repository
public class VersionPolicyPersistenceAdapter implements VersionPolicyPort {

    private final ConcurrentMap<String, VersionPolicy> store = new ConcurrentHashMap<>();

    @Override
    public void save(VersionPolicy policy) {
        store.put(policy.getId().value(), policy);
    }

    @Override
    public Optional<VersionPolicy> findById(VersionPolicyId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public List<VersionPolicy> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(VersionPolicyId id) {
        store.remove(id.value());
    }
}
