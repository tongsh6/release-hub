package io.releasehub.infrastructure.gitlab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockGitLabBranchAdapterTest {

    @Test
    void shouldArchiveBranchWithReason() {
        MockGitLabBranchAdapter adapter = new MockGitLabBranchAdapter();
        String repoUrl = "git@gitlab.com:test/repo.git";

        adapter.addMockBranch(repoUrl, "release/RW-1");

        boolean result = adapter.archiveBranch(repoUrl, "release/RW-1", "unpublished");

        assertTrue(result);
        assertTrue(adapter.getBranches(repoUrl).contains("archive/unpublished/release/RW-1"));
        assertFalse(adapter.getBranches(repoUrl).contains("release/RW-1"));
    }

    @Test
    void shouldReturnFalseWhenBranchNotExists() {
        MockGitLabBranchAdapter adapter = new MockGitLabBranchAdapter();
        String repoUrl = "git@gitlab.com:test/repo.git";

        boolean result = adapter.archiveBranch(repoUrl, "release/RW-2", "unpublished");

        assertFalse(result);
    }
}
