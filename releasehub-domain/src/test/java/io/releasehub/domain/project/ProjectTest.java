package io.releasehub.domain.project;

import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    void create_ShouldSucceed_WhenValid() {
        Instant now = Instant.now();
        Project project = Project.create("Test Project", "Description", now);

        assertNotNull(project.getId());
        assertEquals("Test Project", project.getName());
        assertEquals("Description", project.getDescription());
        assertEquals(ProjectStatus.ACTIVE, project.getStatus());
        assertEquals(now, project.getCreatedAt());
        assertEquals(now, project.getUpdatedAt());
    }

    @Test
    void create_ShouldThrow_WhenNameInvalid() {
        Instant now = Instant.now();
        
        BizException ex1 = assertThrows(BizException.class, () -> Project.create(null, "Desc", now));
        assertEquals("PJ_NAME_REQUIRED", ex1.getCode());

        BizException ex2 = assertThrows(BizException.class, () -> Project.create("", "Desc", now));
        assertEquals("PJ_NAME_REQUIRED", ex2.getCode());

        String longName = "a".repeat(129);
        BizException ex3 = assertThrows(BizException.class, () -> Project.create(longName, "Desc", now));
        assertEquals("PJ_NAME_TOO_LONG", ex3.getCode());
    }

    @Test
    void create_ShouldThrow_WhenDescriptionTooLong() {
        Instant now = Instant.now();
        String longDesc = "a".repeat(513);
        
        BizException ex = assertThrows(BizException.class, () -> Project.create("Name", longDesc, now));
        assertEquals("PJ_DESC_TOO_LONG", ex.getCode());
    }

    @Test
    void rename_ShouldUpdateNameAndTimestamp() {
        Instant now = Instant.now();
        Project project = Project.create("Old Name", "Desc", now);

        Instant later = now.plusSeconds(10);
        project.rename("New Name", later);

        assertEquals("New Name", project.getName());
        assertEquals(later, project.getUpdatedAt());
    }

    @Test
    void archive_ShouldBeIdempotent() {
        Instant now = Instant.now();
        Project project = Project.create("Test", "Desc", now);

        Instant archiveTime = now.plusSeconds(10);
        project.archive(archiveTime);
        assertEquals(ProjectStatus.ARCHIVED, project.getStatus());
        assertEquals(archiveTime, project.getUpdatedAt());

        // Idempotent call
        Instant later = archiveTime.plusSeconds(10);
        project.archive(later);
        assertEquals(ProjectStatus.ARCHIVED, project.getStatus());
        assertEquals(archiveTime, project.getUpdatedAt()); // Should not update timestamp
    }
}
