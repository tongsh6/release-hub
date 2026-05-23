package io.releasehub.infrastructure.persistence.repo;

import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CodeRepositoryPersistenceAdapter 测试")
class CodeRepositoryPersistenceAdapterTest {

    @Test
    @DisplayName("历史 git_provider 为空时按 GITLAB 兼容读取，MOCK 仍按本地 Mock provider 保留")
    void shouldDefaultMissingProviderToGitLabAndKeepMockProviderWhenReadingRepositories() {
        CodeRepositoryJpaRepository repository = mock(CodeRepositoryJpaRepository.class);
        CodeRepositoryPersistenceAdapter adapter = new CodeRepositoryPersistenceAdapter(repository);
        Instant now = Instant.parse("2026-05-22T10:00:00Z");
        CodeRepositoryJpaEntity missingProvider = entity("repo-1", null, now);
        CodeRepositoryJpaEntity legacyProvider = entity("repo-2", "MOCK", now);
        when(repository.findAll()).thenReturn(List.of(missingProvider, legacyProvider));

        var repos = adapter.findAll();

        assertEquals(2, repos.size());
        assertEquals(GitProvider.GITLAB, repos.get(0).getGitProvider());
        assertEquals(GitProvider.MOCK, repos.get(1).getGitProvider());
    }

    private static CodeRepositoryJpaEntity entity(String id, String gitProvider, Instant now) {
        return new CodeRepositoryJpaEntity(
                id,
                "Repo " + id,
                "http://localhost:9080/e2e-user/" + id + ".git",
                "main",
                "G001",
                RepoType.SERVICE.name(),
                gitProvider,
                null,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                now,
                now,
                0L,
                "1.0.0",
                "MANUAL"
        );
    }
}
