package io.releasehub.releasewindow;

import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseWindowTest {

    @Test
    void shouldCreateDraftSuccessfully() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Valid Name", now);

        assertNotNull(rw.getId());
        assertEquals("Valid Name", rw.getName());
        assertEquals(ReleaseWindowStatus.DRAFT, rw.getStatus());
        assertEquals(now, rw.getCreatedAt());
        assertEquals(now, rw.getUpdatedAt());
    }

    @Test
    void shouldFailCreateWhenNameIsInvalid() {
        Instant now = Instant.now();
        assertThrows(BizException.class, () -> ReleaseWindow.createDraft(null, now));
        assertThrows(BizException.class, () -> ReleaseWindow.createDraft("", now));
        assertThrows(BizException.class, () -> ReleaseWindow.createDraft("   ", now));

        String longName = "a".repeat(129);
        assertThrows(BizException.class, () -> ReleaseWindow.createDraft(longName, now));
    }

    @Test
    void shouldTransitionNormally() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);

        // Draft -> Submitted
        rw.submit(now);
        assertEquals(ReleaseWindowStatus.SUBMITTED, rw.getStatus());

        // Submitted -> Released
        rw.release(now);
        assertEquals(ReleaseWindowStatus.RELEASED, rw.getStatus());

        // Released -> Closed
        rw.close(now);
        assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());
    }

    @Test
    void shouldFailOnInvalidTransition() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);

        // Draft -> Released (Invalid)
        BizException e1 = assertThrows(BizException.class, () -> rw.release(now));
        assertEquals("RW_INVALID_STATE", e1.getCode());

        // Draft -> Closed (Invalid)
        BizException e2 = assertThrows(BizException.class, () -> rw.close(now));
        assertEquals("RW_INVALID_STATE", e2.getCode());

        rw.submit(now); // To SUBMITTED
        // Submitted -> Closed (Invalid)
        BizException e3 = assertThrows(BizException.class, () -> rw.close(now));
        assertEquals("RW_INVALID_STATE", e3.getCode());
    }

    @Test
    void shouldBeIdempotentOnClose() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);
        rw.submit(now);
        rw.release(now);
        rw.close(now);

        assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());

        // Call close again
        rw.close(now);
        assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());
    }
}
