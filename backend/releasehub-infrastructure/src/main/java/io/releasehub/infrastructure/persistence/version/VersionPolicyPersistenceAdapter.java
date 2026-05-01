package io.releasehub.infrastructure.persistence.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.version.BumpRule;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import io.releasehub.domain.version.VersionScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * VersionPolicy Port 的 JPA 实现
 */
@Repository
@RequiredArgsConstructor
public class VersionPolicyPersistenceAdapter implements VersionPolicyPort {

    private final VersionPolicyJpaRepository repository;

    @Override
    public void save(VersionPolicy policy) {
        VersionPolicyJpaEntity entity = toEntity(policy);
        repository.save(entity);
    }

    @Override
    public Optional<VersionPolicy> findById(VersionPolicyId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public List<VersionPolicy> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<VersionPolicy> findPaged(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<VersionPolicyJpaEntity> result = repository.searchByKeyword(normalize(keyword), pageable);
        List<VersionPolicy> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    @Override
    public void deleteById(VersionPolicyId id) {
        repository.deleteById(id.value());
    }

    private VersionPolicyJpaEntity toEntity(VersionPolicy domain) {
        return new VersionPolicyJpaEntity(
                domain.getId().value(),
                domain.getName(),
                domain.getScheme().name(),
                domain.getBumpRule().name(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getVersion()
        );
    }

    private VersionPolicy toDomain(VersionPolicyJpaEntity entity) {
        return VersionPolicy.rehydrate(
                VersionPolicyId.of(entity.getId()),
                entity.getName(),
                VersionScheme.valueOf(entity.getScheme()),
                BumpRule.valueOf(entity.getBumpRule()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
