package io.releasehub.infrastructure.persistence.branchrule;

import io.releasehub.application.branchrule.BranchRulePort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;
import io.releasehub.domain.branchrule.BranchRuleScope;
import io.releasehub.domain.branchrule.BranchRuleType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * BranchRule Port 的 JPA 实现
 */
@Repository
@RequiredArgsConstructor
public class BranchRulePersistenceAdapter implements BranchRulePort {

    private final BranchRuleJpaRepository repository;

    @Override
    public void save(BranchRule rule) {
        BranchRuleJpaEntity entity = toEntity(rule);
        repository.save(entity);
    }

    @Override
    public Optional<BranchRule> findById(BranchRuleId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public List<BranchRule> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<BranchRule> findAllEnabled() {
        return repository.findByEnabledTrue().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<BranchRule> findPaged(String name, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<BranchRuleJpaEntity> result;
        if (name == null || name.isBlank()) {
            result = repository.findAll(pageable);
        } else {
            result = repository.findByNameContainingIgnoreCase(name.trim(), pageable);
        }
        List<BranchRule> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    @Override
    public void deleteById(BranchRuleId id) {
        repository.deleteById(id.value());
    }

    private BranchRuleJpaEntity toEntity(BranchRule domain) {
        BranchRuleJpaEntity entity = new BranchRuleJpaEntity();
        entity.setId(domain.getId().value());
        entity.setName(domain.getName());
        entity.setPattern(domain.getPattern());
        entity.setType(domain.getType().name());
        entity.setDescription(domain.getDescription());
        entity.setScopeLevel(domain.getScope().getLevel().name());
        entity.setScopeProjectId(domain.getScope().getProjectId());
        entity.setScopeSubProjectId(domain.getScope().getSubProjectId());
        entity.setEnabled(domain.isEnabled());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVersion(domain.getVersion());
        return entity;
    }

    private BranchRule toDomain(BranchRuleJpaEntity entity) {
        BranchRuleScope scope = buildScope(entity);
        return BranchRule.rehydrate(
                BranchRuleId.of(entity.getId()),
                entity.getName(),
                entity.getPattern(),
                BranchRuleType.valueOf(entity.getType()),
                entity.getDescription(),
                scope,
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private BranchRuleScope buildScope(BranchRuleJpaEntity entity) {
        String level = entity.getScopeLevel();
        if (level == null) {
            return BranchRuleScope.global();
        }
        try {
            BranchRuleScope.ScopeLevel scopeLevel = BranchRuleScope.ScopeLevel.valueOf(level);
            return switch (scopeLevel) {
                case GLOBAL -> BranchRuleScope.global();
                case PROJECT -> BranchRuleScope.project(entity.getScopeProjectId());
                case SUB_PROJECT -> BranchRuleScope.subProject(
                        entity.getScopeProjectId(), entity.getScopeSubProjectId());
            };
        } catch (IllegalArgumentException e) {
            return BranchRuleScope.global();
        }
    }
}
