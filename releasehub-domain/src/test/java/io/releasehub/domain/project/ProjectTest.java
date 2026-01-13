package io.releasehub.domain.project;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        ValidationException ex1 = assertThrows(ValidationException.class, () -> Project.create(null, "Desc", now));
        assertEquals("PJ_002", ex1.getCode());

        ValidationException ex2 = assertThrows(ValidationException.class, () -> Project.create("", "Desc", now));
        assertEquals("PJ_002", ex2.getCode());

        String longName = "a".repeat(129);
        ValidationException ex3 = assertThrows(ValidationException.class, () -> Project.create(longName, "Desc", now));
        assertEquals("PJ_003", ex3.getCode());
    }

    @Test
    void create_ShouldThrow_WhenDescriptionTooLong() {
        Instant now = Instant.now();
        String longDesc = "a".repeat(513);

        ValidationException ex = assertThrows(ValidationException.class, () -> Project.create("Name", longDesc, now));
        assertEquals("PJ_004", ex.getCode());
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
