package io.releasehub.domain.group;

import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GroupTest {

    @Test
    void create_ShouldSucceed_WhenValid() {
        Instant now = Instant.now();
        Group group = Group.create("研发部", "RD", "ORG", now);

        assertNotNull(group.getId());
        assertEquals("研发部", group.getName());
        assertEquals("RD", group.getCode());
        assertEquals("ORG", group.getParentCode());
        assertEquals(now, group.getCreatedAt());
        assertEquals(now, group.getUpdatedAt());
    }

    @Test
    void create_ShouldThrow_WhenNameInvalid() {
        Instant now = Instant.now();

        BizException ex1 = assertThrows(BizException.class, () -> Group.create(null, "CODE", null, now));
        assertEquals("GROUP_NAME_REQUIRED", ex1.getCode());

        BizException ex2 = assertThrows(BizException.class, () -> Group.create("", "CODE", null, now));
        assertEquals("GROUP_NAME_REQUIRED", ex2.getCode());

        String longName = "a".repeat(129);
        BizException ex3 = assertThrows(BizException.class, () -> Group.create(longName, "CODE", null, now));
        assertEquals("GROUP_NAME_TOO_LONG", ex3.getCode());
    }

    @Test
    void create_ShouldThrow_WhenCodeInvalid() {
        Instant now = Instant.now();

        BizException ex1 = assertThrows(BizException.class, () -> Group.create("Name", null, null, now));
        assertEquals("GROUP_CODE_REQUIRED", ex1.getCode());

        BizException ex2 = assertThrows(BizException.class, () -> Group.create("Name", "", null, now));
        assertEquals("GROUP_CODE_REQUIRED", ex2.getCode());

        String longCode = "a".repeat(65);
        BizException ex3 = assertThrows(BizException.class, () -> Group.create("Name", longCode, null, now));
        assertEquals("GROUP_CODE_TOO_LONG", ex3.getCode());
    }

    @Test
    void create_ShouldThrow_WhenParentCodeInvalid() {
        Instant now = Instant.now();

        String longParent = "a".repeat(65);
        BizException ex1 = assertThrows(BizException.class, () -> Group.create("Name", "CODE", longParent, now));
        assertEquals("GROUP_PARENT_CODE_TOO_LONG", ex1.getCode());

        BizException ex2 = assertThrows(BizException.class, () -> Group.create("Name", "CODE", "CODE", now));
        assertEquals("GROUP_PARENT_SAME_AS_SELF", ex2.getCode());
    }

    @Test
    void rename_ShouldUpdateNameAndTimestamp() {
        Instant now = Instant.now();
        Group group = Group.create("旧名", "G1", null, now);

        Instant later = now.plusSeconds(10);
        group.rename("新名", later);

        assertEquals("新名", group.getName());
        assertEquals(later, group.getUpdatedAt());
    }

    @Test
    void changeParentCode_ShouldUpdateAndTouch() {
        Instant now = Instant.now();
        Group group = Group.create("Name", "G1", null, now);

        Instant later = now.plusSeconds(10);
        group.changeParentCode("ORG", later);

        assertEquals("ORG", group.getParentCode());
        assertEquals(later, group.getUpdatedAt());
    }
}

