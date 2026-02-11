package io.releasehub.domain.repo;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeRepositoryTest {

    @Test
    void create_ShouldSucceed_WhenValid() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create("Repo1", "http://git.com/repo1.git", "main", "G001", RepoType.SERVICE, true, now);

        assertNotNull(repo.getId());
        assertEquals("Repo1", repo.getName());
        assertEquals("http://git.com/repo1.git", repo.getCloneUrl());
        assertEquals("main", repo.getDefaultBranch());
        assertEquals(RepoType.SERVICE, repo.getRepoType());
        assertTrue(repo.isMonoRepo());
        assertEquals(now, repo.getCreatedAt());
        assertEquals(now, repo.getUpdatedAt());
    }

    @Test
    void create_ShouldDefaultToService_WhenRepoTypeNull() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create("Repo1", "http://git.com/repo1.git", "main", "G001", null, false, now);

        assertEquals(RepoType.SERVICE, repo.getRepoType());
    }

    @Test
    void create_ShouldSupportLibraryType() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create("CommonUtils", "http://git.com/common-utils.git", "main", "G001", RepoType.LIBRARY, false, now);

        assertEquals(RepoType.LIBRARY, repo.getRepoType());
    }

    @Test
    void update_ShouldChangeRepoType() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create("Repo1", "http://git.com/repo1.git", "main", "G001", RepoType.SERVICE, false, now);

        Instant later = now.plusSeconds(10);
        repo.update("Repo1", "http://git.com/repo1.git", "main", "G001", RepoType.LIBRARY, false, later);

        assertEquals(RepoType.LIBRARY, repo.getRepoType());
        assertEquals(later, repo.getUpdatedAt());
    }

    @Test
    void create_ShouldThrow_WhenInvalid() {
        Instant now = Instant.now();

        // Name checks
        ValidationException exName = assertThrows(ValidationException.class, () -> CodeRepository.create("", "url", "main", "G001", RepoType.SERVICE, false, now));
        assertEquals("REPO_002", exName.getCode());

        String longName = "a".repeat(129);
        ValidationException exNameLong = assertThrows(ValidationException.class, () -> CodeRepository.create(longName, "url", "main", "G001", RepoType.SERVICE, false, now));
        assertEquals("REPO_003", exNameLong.getCode());

        // URL checks
        ValidationException exUrl = assertThrows(ValidationException.class, () -> CodeRepository.create("Name", "", "main", "G001", RepoType.SERVICE, false, now));
        assertEquals("REPO_006", exUrl.getCode());

        String longUrl = "a".repeat(513);
        ValidationException exUrlLong = assertThrows(ValidationException.class, () -> CodeRepository.create("Name", longUrl, "main", "G001", RepoType.SERVICE, false, now));
        assertEquals("REPO_007", exUrlLong.getCode());

        // Branch checks
        ValidationException exBranch = assertThrows(ValidationException.class, () -> CodeRepository.create("Name", "url", "", "G001", RepoType.SERVICE, false, now));
        assertEquals("REPO_008", exBranch.getCode());

        // Group checks
        ValidationException exGroup = assertThrows(ValidationException.class, () -> CodeRepository.create("Name", "url", "main", " ", RepoType.SERVICE, false, now));
        assertEquals("GROUP_005", exGroup.getCode());

        String longGroup = "G".repeat(65);
        ValidationException exGroupLong = assertThrows(ValidationException.class, () -> CodeRepository.create("Name", "url", "main", longGroup, RepoType.SERVICE, false, now));
        assertEquals("GROUP_006", exGroupLong.getCode());
    }

    @Test
    void changeDefaultBranch_ShouldUpdate() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create("Name", "url", "main", "G001", RepoType.SERVICE, false, now);

        Instant later = now.plusSeconds(10);
        repo.changeDefaultBranch("develop", later);

        assertEquals("develop", repo.getDefaultBranch());
        assertEquals(later, repo.getUpdatedAt());
    }
}
