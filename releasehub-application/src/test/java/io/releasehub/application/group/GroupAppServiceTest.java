package io.releasehub.application.group;

import io.releasehub.domain.group.Group;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GroupAppServiceTest {

    static class InMemoryPort implements GroupPort {
        private final Map<String, Group> byId = new HashMap<>();
        @Override
        public void save(Group group) {
            byId.put(group.getId().value(), group);
        }
        @Override
        public Optional<Group> findById(io.releasehub.domain.group.GroupId id) {
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
        public void deleteById(io.releasehub.domain.group.GroupId id) { byId.remove(id.value()); }
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
    void childrenAndTopLevelAndTree_ShouldWork() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port);
        Instant now = Instant.now();

        Group a = Group.create("A", "A", null, now);
        Group b = Group.create("B", "B", "A", now);
        Group c = Group.create("C", "C", "B", now);
        Group d = Group.create("D", "D", null, now);
        port.save(a);
        port.save(b);
        port.save(c);
        port.save(d);

        var top = svc.topLevel();
        assertEquals(2, top.size());
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals("A")));
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals("D")));

        var childrenA = svc.children("A");
        assertEquals(1, childrenA.size());
        assertEquals("B", childrenA.get(0).getCode());

        var tree = svc.tree();
        assertEquals(2, tree.size());
        var aNodeOpt = tree.stream().filter(n -> n.getCode().equals("A")).findFirst();
        assertTrue(aNodeOpt.isPresent());
        var aNode = aNodeOpt.get();
        assertEquals(1, aNode.getChildren().size());
        assertEquals("B", aNode.getChildren().get(0).getCode());
        assertEquals(1, aNode.getChildren().get(0).getChildren().size());
        assertEquals("C", aNode.getChildren().get(0).getChildren().get(0).getCode());
    }
}
