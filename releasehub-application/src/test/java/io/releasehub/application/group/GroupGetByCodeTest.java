package io.releasehub.application.group;

import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GroupGetByCodeTest {

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
        public void deleteById(GroupId id) { byId.remove(id.value()); }
        @Override
        public void deleteByCode(String code) {
            String toRemove = null;
            for (Group g : byId.values()) if (g.getCode().equals(code)) toRemove = g.getId().value();
            if (toRemove != null) byId.remove(toRemove);
        }
        @Override
        public long countChildren(String parentCode) {
            long cnt = 0;
            for (Group g : byId.values()) if (Objects.equals(parentCode, g.getParentCode())) cnt++;
            return cnt;
        }
    }

    @Test
    void getByCode_ShouldReturnGroup_WhenExists() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port);
        Instant now = Instant.now();
        Group a = Group.create("A", "001", null, now);
        Group b = Group.create("B", "001001", "001", now);
        port.save(a);
        port.save(b);

        Group got = svc.getByCode("001001");
        assertEquals("B", got.getName());
        assertEquals("001", got.getParentCode());
    }

    @Test
    void getByCode_ShouldThrow_WhenNotExists() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port);

        BizException ex = assertThrows(BizException.class, () -> svc.getByCode("NOPE"));
        assertEquals("GROUP_CODE_NOT_FOUND", ex.getCode());
    }
}
