package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.repo.GitProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitBranchAdapterFactoryImplTest {

    private final RestTemplateBuilder builder = new RestTemplateBuilder();

    @Test
    void shouldReturnMatchedAdapter() {
        GitBranchPort inMemoryGitLab = new InMemoryGitLabBranchAdapter();
        GitBranchPort github = new GitHubGitBranchAdapter(builder);

        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(github, inMemoryGitLab));

        assertSame(github, factory.getAdapter(GitProvider.GITHUB));
        assertSame(inMemoryGitLab, factory.getAdapter(GitProvider.GITLAB));
    }

    @Test
    void shouldRejectNullProvider() {
        GitBranchPort inMemoryGitLab = new InMemoryGitLabBranchAdapter();
        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(inMemoryGitLab));

        assertThrows(ValidationException.class, () -> factory.getAdapter(null));
    }

    @Test
    void shouldThrowWhenNoAdapterSupportsProvider() {
        GitBranchPort inMemoryGitLab = new InMemoryGitLabBranchAdapter();
        GitBranchAdapterFactoryImpl factory = new GitBranchAdapterFactoryImpl(List.of(inMemoryGitLab));

        assertThrows(ValidationException.class, () -> factory.getAdapter(GitProvider.GITHUB));
    }
}
