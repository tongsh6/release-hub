package io.releasehub.application.group;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAppService {
    private final GroupPort groupPort;
    private final Clock clock = Clock.systemUTC();
    private static final String ROOT = "__ROOT__";

    @Transactional
    public Group create(String name, String code, String parentCode) {
        ensureCodeAvailable(code);
        String normalizedParent = normalizeParentCode(parentCode);
        if (normalizedParent != null && normalizedParent.equals(code)) {
            throw BusinessException.groupParentSelf();
        }
        ensureParentExists(normalizedParent);

        Group group = Group.create(name, code, normalizedParent, Instant.now(clock));
        groupPort.save(group);
        return group;
    }

    public Group get(String id) {
        return groupPort.findById(new GroupId(id))
                .orElseThrow(() -> NotFoundException.group(id));
    }

    public Group getByCode(String code) {
        return groupPort.findByCode(code)
                .orElseThrow(() -> NotFoundException.groupCode(code));
    }

    @Transactional
    public Group update(String id, String name, String parentCode) {
        Group group = get(id);
        String normalizedParent = normalizeParentCode(parentCode);
        ensureParentExists(normalizedParent);

        if (normalizedParent != null && normalizedParent.equals(group.getCode())) {
            throw BusinessException.groupParentSelf();
        }

        Instant now = Instant.now(clock);
        group.rename(name, now);
        group.changeParentCode(normalizedParent, now);
        groupPort.save(group);
        return group;
    }

    @Transactional
    public void deleteById(String id) {
        Group g = get(id);
        long cnt = groupPort.countChildren(g.getCode());
        if (cnt > 0) {
            throw BusinessException.groupHasChildren(g.getCode());
        }
        groupPort.deleteById(new GroupId(id));
    }

    @Transactional
    public void deleteByCode(String code) {
        long cnt = groupPort.countChildren(code);
        if (cnt > 0) {
            throw BusinessException.groupHasChildren(code);
        }
        groupPort.deleteByCode(code);
    }
    public List<Group> list() {
        return groupPort.findAll();
    }

    public List<Group> children(String parentCode) {
        return groupPort.findByParentCode(normalizeParentCode(parentCode));
    }

    public List<Group> topLevel() {
        return groupPort.findTopLevel();
    }

    public List<GroupNodeView> tree() {
        List<Group> all = groupPort.findAll();
        Map<String, List<Group>> byParent = all.stream()
                .collect(Collectors.groupingBy(g -> {
                    String pc = g.getParentCode();
                    return pc == null ? ROOT : pc;
                }));
        Set<String> codes = all.stream().map(Group::getCode).collect(Collectors.toSet());
        List<Group> roots = byParent.get(ROOT);
        if (roots == null) {
            return List.of();
        }
        return roots.stream()
                .map(root -> buildNode(root, byParent, codes))
                .collect(Collectors.toList());
    }

    private GroupNodeView buildNode(Group group, Map<String, List<Group>> byParent, Set<String> codes) {
        GroupNodeView node = GroupNodeView.fromDomain(group);
        List<Group> children = byParent.get(group.getCode());
        if (children == null || children.isEmpty()) {
            node.setChildren(List.of());
            return node;
        }
        List<GroupNodeView> childNodes = children.stream()
                .filter(g -> !Objects.equals(g.getCode(), group.getCode()))
                .map(g -> buildNode(g, byParent, codes))
                .collect(Collectors.toList());
        node.setChildren(childNodes);
        return node;
    }

    private String normalizeParentCode(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        return parentCode;
    }

    private void ensureCodeAvailable(String code) {
        if (groupPort.findByCode(code).isPresent()) {
            throw BusinessException.groupCodeExists(code);
        }
    }

    private void ensureParentExists(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return;
        }
        groupPort.findByCode(parentCode)
                .orElseThrow(() -> NotFoundException.groupParent(parentCode));
    }
}
