package io.releasehub.releasewindow;

import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNull(rw.getStartAt());
        assertNull(rw.getEndAt());
        assertFalse(rw.isFrozen());
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

    @Test
    void shouldConfigureWindowSuccessfully() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);

        Instant start = now.plusSeconds(3600);
        Instant end = now.plusSeconds(7200);

        rw.configureWindow(start, end, now);

        assertEquals(start, rw.getStartAt());
        assertEquals(end, rw.getEndAt());
    }

    @Test
    void shouldFailConfigureWindowWhenInvalid() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);

        Instant start = now.plusSeconds(3600);
        Instant end = now.plusSeconds(7200);

        // Null checks
        assertThrows(BizException.class, () -> rw.configureWindow(null, end, now));
        assertThrows(BizException.class, () -> rw.configureWindow(start, null, now));

        // Start >= End
        assertThrows(BizException.class, () -> rw.configureWindow(end, start, now));
        assertThrows(BizException.class, () -> rw.configureWindow(start, start, now));
    }

    @Test
    void shouldFreezeAndUnfreezeSuccessfully() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);
        rw.submit(now);

        // Freeze
        rw.freeze(now);
        assertTrue(rw.isFrozen());

        // Idempotent Freeze
        rw.freeze(now);
        assertTrue(rw.isFrozen());

        // Unfreeze
        rw.unfreeze(now);
        assertFalse(rw.isFrozen());

        // Idempotent Unfreeze
        rw.unfreeze(now);
        assertFalse(rw.isFrozen());
    }

    @Test
    void shouldFailFreezeWhenNotInSubmitted() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);

        assertThrows(BizException.class, () -> rw.freeze(now));
    }

    @Test
    void shouldFailReleaseWhenFrozen() {
        Instant now = Instant.now();
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);
        rw.submit(now);
        rw.freeze(now);

        BizException e = assertThrows(BizException.class, () -> rw.release(now));
        assertEquals("RW_FROZEN", e.getCode());

        rw.unfreeze(now);
        rw.release(now);
        assertEquals(ReleaseWindowStatus.RELEASED, rw.getStatus());
    }

    @Test
    void shouldFailReleaseWhenOutsideWindow() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        ReleaseWindow rw = ReleaseWindow.createDraft("Test", now);
        rw.submit(now);

        Instant start = Instant.parse("2025-01-01T13:00:00Z");
        Instant end = Instant.parse("2025-01-01T14:00:00Z");
        rw.configureWindow(start, end, now);

        // Before window
        BizException e1 = assertThrows(BizException.class, () -> rw.release(Instant.parse("2025-01-01T12:59:59Z")));
        assertEquals("RW_OUT_OF_WINDOW", e1.getCode());

        // After window
        BizException e2 = assertThrows(BizException.class, () -> rw.release(Instant.parse("2025-01-01T14:00:01Z")));
        assertEquals("RW_OUT_OF_WINDOW", e2.getCode());

        // Inside window
        rw.release(Instant.parse("2025-01-01T13:30:00Z"));
        assertEquals(ReleaseWindowStatus.RELEASED, rw.getStatus());
    }
}
