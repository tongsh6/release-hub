package io.releasehub.application.group;

import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;

import java.util.List;
import java.util.Optional;

public interface GroupPort {
    void save(Group group);
    Optional<Group> findById(GroupId id);
    Optional<Group> findByCode(String code);
    List<Group> findAll();
    List<Group> findByParentCode(String parentCode);
    List<Group> findTopLevel();
    void deleteById(GroupId id);
    void deleteByCode(String code);
    long countChildren(String parentCode);
}
