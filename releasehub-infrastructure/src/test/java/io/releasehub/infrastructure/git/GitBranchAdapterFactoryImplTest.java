package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.repo.GitProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitBranchAdapterFactoryImplTest {

    @Test
    void shouldReturnMatchedAdapter() {
        GitBranchPort mock = new MockGitBranchAdapter();
        GitBranchPort github = new GitHubGitBranchAdapter();
        GitBranchPort gitlab = new GitLabGitBranchAdapter();

        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(mock, github, gitlab));

        assertSame(github, factory.getAdapter(GitProvider.GITHUB));
        assertSame(gitlab, factory.getAdapter(GitProvider.GITLAB));
        assertSame(mock, factory.getAdapter(GitProvider.MOCK));
    }

    @Test
    void shouldFallbackToMockWhenProviderIsNull() {
        GitBranchPort mock = new MockGitBranchAdapter();
        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(mock));

        assertSame(mock, factory.getAdapter(null));
    }

    @Test
    void shouldThrowWhenNoAdapterSupportsProvider() {
        GitBranchPort mockOnly = new MockGitBranchAdapter();
        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(mockOnly));

        assertThrows(ValidationException.class, () -> factory.getAdapter(GitProvider.GITHUB));
    }
}
