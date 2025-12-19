package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReleaseWindow 聚合状态机单测
 * 覆盖关键路径：创建 -> 配置 -> 冻结 -> 发布 -> Release -> Close
 */
class ReleaseWindowTest {

    private final Instant now = Instant.now();

    @Test
    void should_create_draft_successfully() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        assertEquals(ReleaseWindowStatus.DRAFT, rw.getStatus());
        assertEquals("2024-W01", rw.getName());
        assertFalse(rw.isFrozen());
    }

    @Test
    void should_configure_window_successfully() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        Instant start = now.plus(1, ChronoUnit.DAYS);
        Instant end = now.plus(3, ChronoUnit.DAYS);

        rw.configureWindow(start, end, now);

        assertEquals(start, rw.getStartAt());
        assertEquals(end, rw.getEndAt());
    }

    @Test
    void should_fail_to_configure_when_frozen() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        rw.freeze(now);

        Instant start = now.plus(1, ChronoUnit.DAYS);
        Instant end = now.plus(3, ChronoUnit.DAYS);

        BizException ex = assertThrows(BizException.class, () -> rw.configureWindow(start, end, now));
        assertTrue(ex.getMessage().contains("Cannot configure frozen ReleaseWindow"));
    }

    @Test
    void should_fail_to_publish_when_not_configured() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);

        BizException ex = assertThrows(BizException.class, () -> rw.publish(now));
        assertTrue(ex.getMessage().contains("must be configured"));
    }

    @Test
    void should_publish_successfully() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        rw.configureWindow(now.plus(1, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), now);
        
        rw.publish(now);

        assertEquals(ReleaseWindowStatus.PUBLISHED, rw.getStatus());
        assertEquals(now, rw.getPublishedAt());
    }

    @Test
    void should_fail_to_publish_when_already_published() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        rw.configureWindow(now.plus(1, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), now);
        rw.publish(now);

        BizException ex = assertThrows(BizException.class, () -> rw.publish(now));
        assertTrue(ex.getMessage().contains("Cannot publish from state"));
    }

    @Test
    void should_freeze_successfully() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        rw.freeze(now);
        assertTrue(rw.isFrozen());
    }

    @Test
    void should_release_successfully() {
        ReleaseWindow rw = ReleaseWindow.createDraft("2024-W01", now);
        rw.configureWindow(now.plus(1, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), now);
        rw.publish(now);
        
        rw.release(now);
        assertEquals(ReleaseWindowStatus.RELEASED, rw.getStatus());
    }
}
