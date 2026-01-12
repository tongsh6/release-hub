package io.releasehub.infrastructure.persistence.group;

import io.releasehub.application.group.GroupPort;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class GroupJpaPersistenceAdapter implements GroupPort {

    private final GroupJpaRepository repository;

    @Override
    public void save(Group group) {
        GroupJpaEntity entity = new GroupJpaEntity(
                group.getId().value(),
                group.getName(),
                group.getCode(),
                group.getParentCode(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                group.getVersion()
        );
        repository.save(entity);
    }

    @Override
    public Optional<Group> findById(GroupId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<Group> findByCode(String code) {
        return repository.findByCode(code)
                .map(this::toDomain);
    }

    @Override
    public List<Group> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> findByParentCode(String parentCode) {
        if (parentCode == null) {
            return repository.findByParentCodeIsNull().stream()
                    .map(this::toDomain)
                    .collect(Collectors.toList());
        }
        return repository.findByParentCode(parentCode).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> findTopLevel() {
        return repository.findByParentCodeIsNull().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(GroupId id) {
        repository.deleteById(id.value());
    }

    @Override
    public void deleteByCode(String code) {
        repository.deleteByCode(code);
    }

    @Override
    public long countChildren(String parentCode) {
        return repository.countByParentCode(parentCode);
    }

    private Group toDomain(GroupJpaEntity entity) {
        return Group.rehydrate(
                GroupId.of(entity.getId()),
                entity.getName(),
                entity.getCode(),
                entity.getParentCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
