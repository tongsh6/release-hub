package io.releasehub.infrastructure.persistence.group;

import io.releasehub.application.group.GroupPort;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class GroupPersistenceAdapter implements GroupPort {

    private final Map<GroupId, Group> store = new ConcurrentHashMap<>();

    @Override
    public void save(Group group) {
        store.put(group.getId(), group);
    }

    @Override
    public Optional<Group> findById(GroupId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Group> findByCode(String code) {
        return store.values().stream()
                .filter(g -> g.getCode().equals(code))
                .findFirst();
    }

    @Override
    public List<Group> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Group> findByParentCode(String parentCode) {
        return store.values().stream()
                .filter(g -> {
                    String pc = g.getParentCode();
                    if (parentCode == null) {
                        return pc == null;
                    }
                    return parentCode.equals(pc);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> findTopLevel() {
        return store.values().stream()
                .filter(g -> g.getParentCode() == null)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(GroupId id) {
        store.remove(id);
    }

    @Override
    public void deleteByCode(String code) {
        store.entrySet().removeIf(e -> e.getValue().getCode().equals(code));
    }

    @Override
    public long countChildren(String parentCode) {
        return store.values().stream()
                .filter(g -> {
                    String pc = g.getParentCode();
                    if (parentCode == null) {
                        return pc == null;
                    }
                    return parentCode.equals(pc);
                })
                .count();
    }
}
