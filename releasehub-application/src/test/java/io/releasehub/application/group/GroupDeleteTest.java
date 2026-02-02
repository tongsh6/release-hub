package io.releasehub.application.group;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Replaced by unitTest-profile H2 IT in bootstrap")
class GroupDeleteTest {
    @Test
    void deleteByCode_ShouldSucceed_ForLeaf() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port, new EmptyReleaseWindowPort(), new EmptyIterationPort(), new EmptyRepoPort());
        Instant now = Instant.now();
        Group a = Group.create("A", "001", null, now);
        Group b = Group.create("B", "001001", "001", now);
        port.save(a);
        port.save(b);
        svc.deleteByCode("001001");
        assertTrue(port.findByCode("001001").isEmpty());
    }

    @Test
    void deleteByCode_ShouldFail_WhenHasChildren() {
        InMemoryPort port = new InMemoryPort();
        GroupAppService svc = new GroupAppService(port, new EmptyReleaseWindowPort(), new EmptyIterationPort(), new EmptyRepoPort());
        Instant now = Instant.now();
        Group a = Group.create("A", "001", null, now);
        Group b = Group.create("B", "001001", "001", now);
        Group c = Group.create("C", "001001001", "001001", now);
        port.save(a);
        port.save(b);
        port.save(c);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deleteByCode("001001"));
        assertEquals("GROUP_008", ex.getCode());
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
                if (Objects.equals(parentCode, g.getParentCode())) list.add(g);
            }
            return list;
        }

        @Override
        public List<Group> findTopLevel() {
            List<Group> list = new ArrayList<>();
            for (Group g : byId.values()) if (g.getParentCode() == null) list.add(g);
            return list;
        }

        @Override
        public void deleteById(GroupId id) {
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
        public void save(ReleaseWindow releaseWindow) {
        }

        @Override
        public Optional<ReleaseWindow> findById(ReleaseWindowId id) {
            return Optional.empty();
        }

        @Override
        public List<ReleaseWindow> findAll() {
            return List.of();
        }

        @Override
        public PageResult<ReleaseWindow> findPaged(String name, int page, int size) {
            return new PageResult<>(List.of(), 0);
        }
    }

    static class EmptyIterationPort implements IterationPort {
        @Override
        public void save(Iteration iteration) {
        }

        @Override
        public Optional<Iteration> findByKey(IterationKey key) {
            return Optional.empty();
        }

        @Override
        public List<Iteration> findAll() {
            return List.of();
        }

        @Override
        public PageResult<Iteration> findPaged(String keyword, int page, int size) {
            return new PageResult<>(List.of(), 0);
        }

        @Override
        public void deleteByKey(IterationKey key) {
        }
    }

    static class EmptyRepoPort implements CodeRepositoryPort {
        @Override
        public void save(CodeRepository domain) {
        }

        @Override
        public Optional<CodeRepository> findById(RepoId id) {
            return Optional.empty();
        }

        @Override
        public List<CodeRepository> findAll() {
            return List.of();
        }

        @Override
        public void deleteById(RepoId id) {
        }

        @Override
        public List<CodeRepository> search(String keyword) {
            return List.of();
        }

        @Override
        public PageResult<CodeRepository> searchPaged(String keyword, int page, int size) {
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
