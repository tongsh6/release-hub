package io.releasehub.domain.repo;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.project.ProjectId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CodeRepositoryTest {

    @Test
    void create_ShouldSucceed_WhenValid() {
        Instant now = Instant.now();
        ProjectId projectId = ProjectId.newId();
        CodeRepository repo = CodeRepository.create(projectId, "Repo1", "http://git.com/repo1.git", "main", true, now);

        assertNotNull(repo.getId());
        assertEquals(projectId, repo.getProjectId());
        assertEquals("Repo1", repo.getName());
        assertEquals("http://git.com/repo1.git", repo.getCloneUrl());
        assertEquals("main", repo.getDefaultBranch());
        assertTrue(repo.isMonoRepo());
        assertEquals(now, repo.getCreatedAt());
        assertEquals(now, repo.getUpdatedAt());
    }

    @Test
    void create_ShouldThrow_WhenInvalid() {
        Instant now = Instant.now();
        ProjectId projectId = ProjectId.newId();

        // Missing ProjectId
        assertThrows(BizException.class, () -> CodeRepository.create(null, "Name", "url", "main", false, now));

        // Name checks
        BizException exName = assertThrows(BizException.class, () -> CodeRepository.create(projectId, "", "url", "main", false, now));
        assertEquals("REPO_NAME_REQUIRED", exName.getCode());

        String longName = "a".repeat(129);
        BizException exNameLong = assertThrows(BizException.class, () -> CodeRepository.create(projectId, longName, "url", "main", false, now));
        assertEquals("REPO_NAME_TOO_LONG", exNameLong.getCode());

        // URL checks
        BizException exUrl = assertThrows(BizException.class, () -> CodeRepository.create(projectId, "Name", "", "main", false, now));
        assertEquals("REPO_URL_REQUIRED", exUrl.getCode());

        String longUrl = "a".repeat(513);
        BizException exUrlLong = assertThrows(BizException.class, () -> CodeRepository.create(projectId, "Name", longUrl, "main", false, now));
        assertEquals("REPO_URL_TOO_LONG", exUrlLong.getCode());

        // Branch checks
        BizException exBranch = assertThrows(BizException.class, () -> CodeRepository.create(projectId, "Name", "url", "", false, now));
        assertEquals("REPO_BRANCH_REQUIRED", exBranch.getCode());
    }

    @Test
    void changeDefaultBranch_ShouldUpdate() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.create(ProjectId.newId(), "Name", "url", "main", false, now);

        Instant later = now.plusSeconds(10);
        repo.changeDefaultBranch("develop", later);

        assertEquals("develop", repo.getDefaultBranch());
        assertEquals(later, repo.getUpdatedAt());
    }
}
