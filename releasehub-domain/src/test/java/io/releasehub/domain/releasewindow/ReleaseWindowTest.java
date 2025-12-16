package io.releasehub.domain.releasewindow;

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
        Instant submitTime = now.plusSeconds(10);
        rw.submit(submitTime);
        assertEquals(ReleaseWindowStatus.SUBMITTED, rw.getStatus());
        assertEquals(submitTime, rw.getUpdatedAt());

        // Submitted -> Released
        Instant releaseTime = now.plusSeconds(20);
        rw.release(releaseTime);
        assertEquals(ReleaseWindowStatus.RELEASED, rw.getStatus());
        assertEquals(releaseTime, rw.getUpdatedAt());

        // Released -> Closed
        Instant closeTime = now.plusSeconds(30);
        rw.close(closeTime);
        assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());
        assertEquals(closeTime, rw.getUpdatedAt());
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

        Instant updateTime = now.plusSeconds(10);
        rw.configureWindow(start, end, updateTime);

        assertEquals(start, rw.getStartAt());
        assertEquals(end, rw.getEndAt());
        assertEquals(updateTime, rw.getUpdatedAt());
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
        Instant freezeTime = now.plusSeconds(10);
        rw.freeze(freezeTime);
        assertTrue(rw.isFrozen());
        assertEquals(freezeTime, rw.getUpdatedAt());

        // Idempotent Freeze - should NOT update timestamp
        Instant later = freezeTime.plusSeconds(10);
        rw.freeze(later);
        assertTrue(rw.isFrozen());
        assertEquals(freezeTime, rw.getUpdatedAt()); // Timestamp remains freezeTime

        // Unfreeze
        Instant unfreezeTime = later.plusSeconds(10);
        rw.unfreeze(unfreezeTime);
        assertFalse(rw.isFrozen());
        assertEquals(unfreezeTime, rw.getUpdatedAt());

        // Idempotent Unfreeze - should NOT update timestamp
        Instant evenLater = unfreezeTime.plusSeconds(10);
        rw.unfreeze(evenLater);
        assertFalse(rw.isFrozen());
        assertEquals(unfreezeTime, rw.getUpdatedAt()); // Timestamp remains unfreezeTime
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
