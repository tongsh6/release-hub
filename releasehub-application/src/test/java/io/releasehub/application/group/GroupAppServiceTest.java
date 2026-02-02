package io.releasehub.application.group;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.group.Group;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Replaced by unitTest-profile H2 IT in bootstrap")
class GroupAppServiceTest {

    @Test
    void childrenAndTopLevelAndTree_ShouldWork() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port, new EmptyReleaseWindowPort(), new EmptyIterationPort(), new EmptyRepoPort());
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
        public PageResult<Group> findPaged(int page, int size) {
            List<Group> all = findAll();
            int pageIndex = Math.max(page - 1, 0);
            int from = Math.min(pageIndex * size, all.size());
            int to = Math.min(from + size, all.size());
            return new PageResult<>(all.subList(from, to), all.size());
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
        public void deleteById(io.releasehub.domain.group.GroupId id) {
            byId.remove(id.value());
        }

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

    static class EmptyReleaseWindowPort implements ReleaseWindowPort {
        @Override
        public void save(io.releasehub.domain.releasewindow.ReleaseWindow releaseWindow) {
        }

        @Override
        public Optional<io.releasehub.domain.releasewindow.ReleaseWindow> findById(io.releasehub.domain.releasewindow.ReleaseWindowId id) {
            return Optional.empty();
        }

        @Override
        public List<io.releasehub.domain.releasewindow.ReleaseWindow> findAll() {
            return List.of();
        }

        @Override
        public PageResult<io.releasehub.domain.releasewindow.ReleaseWindow> findPaged(String name, int page, int size) {
            return new PageResult<>(List.of(), 0);
        }
    }

    static class EmptyIterationPort implements IterationPort {
        @Override
        public void save(io.releasehub.domain.iteration.Iteration iteration) {
        }

        @Override
        public Optional<io.releasehub.domain.iteration.Iteration> findByKey(io.releasehub.domain.iteration.IterationKey key) {
            return Optional.empty();
        }

        @Override
        public List<io.releasehub.domain.iteration.Iteration> findAll() {
            return List.of();
        }

        @Override
        public PageResult<io.releasehub.domain.iteration.Iteration> findPaged(String keyword, int page, int size) {
            return new PageResult<>(List.of(), 0);
        }

        @Override
        public void deleteByKey(io.releasehub.domain.iteration.IterationKey key) {
        }
    }

    static class EmptyRepoPort implements CodeRepositoryPort {
        @Override
        public void save(io.releasehub.domain.repo.CodeRepository domain) {
        }

        @Override
        public Optional<io.releasehub.domain.repo.CodeRepository> findById(io.releasehub.domain.repo.RepoId id) {
            return Optional.empty();
        }

        @Override
        public List<io.releasehub.domain.repo.CodeRepository> findAll() {
            return List.of();
        }

        @Override
        public void deleteById(io.releasehub.domain.repo.RepoId id) {
        }

        @Override
        public List<io.releasehub.domain.repo.CodeRepository> search(String keyword) {
            return List.of();
        }

        @Override
        public PageResult<io.releasehub.domain.repo.CodeRepository> searchPaged(String keyword, int page, int size) {
            return new PageResult<>(List.of(), 0);
        }

        @Override
        public void updateInitialVersion(String repoId, String initialVersion, String versionSource) {
        }

        @Override
        public Optional<String> getInitialVersion(String repoId) {
            return Optional.empty();
        }
    }
}
