package io.releasehub.application.group;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
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
    private static final String ROOT = "__ROOT__";
    private final GroupPort groupPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final IterationPort iterationPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Group create(String name, String code, String parentCode) {
        String normalizedParent = normalizeParentCode(parentCode);
        ensureParentExists(normalizedParent);

        String finalCode = code;
        if (finalCode == null || finalCode.isBlank()) {
            finalCode = generateCode(normalizedParent);
        }
        ensureCodeAvailable(finalCode);

        if (normalizedParent != null && normalizedParent.equals(finalCode)) {
            throw BusinessException.groupParentSelf();
        }

        Group group = Group.create(name, finalCode, normalizedParent, Instant.now(clock));
        groupPort.save(group);
        return group;
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

    private String generateCode(String parentCode) {
        List<Group> siblings = parentCode == null ? groupPort.findTopLevel() : groupPort.findByParentCode(parentCode);
        int max = 0;
        for (Group g : siblings) {
            String code = g.getCode();
            if (parentCode == null) {
                if (code != null && code.length() == 3) {
                    max = Math.max(max, parseCode(code));
                }
            } else if (code != null && code.startsWith(parentCode) && code.length() == parentCode.length() + 3) {
                max = Math.max(max, parseCode(code.substring(parentCode.length())));
            }
        }
        int next = max + 1;
        String suffix = String.format("%03d", next);
        return parentCode == null ? suffix : parentCode + suffix;
    }

    private int parseCode(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    public Group get(String id) {
        return groupPort.findById(GroupId.of(id))
                        .orElseThrow(() -> NotFoundException.group(id));
    }

    @Transactional
    public void deleteById(String id) {
        Group g = get(id);
        long cnt = groupPort.countChildren(g.getCode());
        if (cnt > 0) {
            throw BusinessException.groupHasChildren(g.getCode());
        }
        ensureNotReferenced(g.getCode());
        groupPort.deleteById(GroupId.of(id));
    }

    private void ensureNotReferenced(String groupCode) {
        boolean referenced = releaseWindowPort.findAll().stream()
                                              .anyMatch(w -> groupCode.equals(w.getGroupCode()));
        if (!referenced) {
            referenced = iterationPort.findAll().stream()
                                      .anyMatch(it -> groupCode.equals(it.getGroupCode()));
        }
        if (!referenced) {
            referenced = codeRepositoryPort.findAll().stream()
                                           .anyMatch(repo -> groupCode.equals(repo.getGroupCode()));
        }
        if (referenced) {
            throw BusinessException.groupReferenced(groupCode);
        }
    }

    @Transactional
    public void deleteByCode(String code) {
        long cnt = groupPort.countChildren(code);
        if (cnt > 0) {
            throw BusinessException.groupHasChildren(code);
        }
        ensureNotReferenced(code);
        groupPort.deleteByCode(code);
    }

    public List<Group> list() {
        return groupPort.findAll();
    }

    public PageResult<Group> listPaged(int page, int size) {
        return groupPort.findPaged(page, size);
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
}
