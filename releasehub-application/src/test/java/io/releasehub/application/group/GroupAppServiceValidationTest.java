package io.releasehub.application.group;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupAppServiceValidationTest {

    private InMemoryPort port;
    private GroupAppService svc;
    private Instant now;

    @BeforeEach
    void setUp() {
        port = new InMemoryPort();
        svc = new GroupAppService(port);
        now = Instant.now();
    }

    @Test
    void createShouldFailWhenCodeExists() {
        port.save(Group.create("A", "A", null, now));

        BizException ex = assertThrows(BizException.class, () -> svc.create("Another", "A", null));
        assertEquals("GROUP_CODE_EXISTS", ex.getCode());
    }

    @Test
    void createShouldFailWhenParentMissing() {
        BizException ex = assertThrows(BizException.class, () -> svc.create("Child", "C", "NO_PARENT"));
        assertEquals("GROUP_PARENT_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateShouldFailWhenParentIsSelf() {
        port.save(Group.create("Self", "SELF", null, now));

        BizException ex = assertThrows(BizException.class, () -> svc.update("SELF", "Self", "SELF"));
        assertEquals("GROUP_PARENT_SAME_AS_SELF", ex.getCode());
    }

    @Test
    void updateShouldFailWhenParentMissing() {
        port.save(Group.create("Node", "NODE", null, now));

        BizException ex = assertThrows(BizException.class, () -> svc.update("NODE", "Node2", "MISSING"));
        assertEquals("GROUP_PARENT_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateShouldSucceedWhenValidParent() {
        port.save(Group.create("Parent", "PARENT", null, now));
        port.save(Group.create("Child", "CHILD", null, now));

        Group updated = svc.update("CHILD", "ChildRenamed", "PARENT");

        assertEquals("ChildRenamed", updated.getName());
        assertEquals("PARENT", updated.getParentCode());
    }

    static class InMemoryPort implements GroupPort {
        private final Map<String, Group> byId = new HashMap<>();

        @Override
        public void save(Group group) {
            byId.put(group.getId().value(), group);
        }

        @Override
        public Optional<Group> findById(GroupId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public Optional<Group> findByCode(String code) {
            return byId.values().stream().filter(g -> g.getCode().equals(code)).findFirst();
        }

        @Override
        public List<Group> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public List<Group> findByParentCode(String parentCode) {
            List<Group> list = new ArrayList<>();
            for (Group g : byId.values()) {
                if (Objects.equals(parentCode, g.getParentCode())) {
                    list.add(g);
                }
            }
            return list;
        }

        @Override
        public List<Group> findTopLevel() {
            List<Group> list = new ArrayList<>();
            for (Group g : byId.values()) {
                if (g.getParentCode() == null) {
                    list.add(g);
                }
            }
            return list;
        }

        @Override
        public void deleteById(GroupId id) {
            byId.remove(id.value());
        }

        @Override
        public void deleteByCode(String code) {
            String toRemove = null;
            for (Group g : byId.values()) {
                if (g.getCode().equals(code)) {
                    toRemove = g.getId().value();
                    break;
                }
            }
            if (toRemove != null) {
                byId.remove(toRemove);
            }
        }

        @Override
        public long countChildren(String parentCode) {
            return byId.values().stream().filter(g -> Objects.equals(parentCode, g.getParentCode())).count();
        }
    }
}
