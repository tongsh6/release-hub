package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReleaseWindow 聚合状态机单测
 * 覆盖关键路径：创建 -> 发布 -> 关闭
 * 状态流转：DRAFT → PUBLISHED → CLOSED
 */
@DisplayName("ReleaseWindow 领域实体测试")
class ReleaseWindowTest {

    private final Instant now = Instant.now();
    private final Instant plannedRelease = now.plusSeconds(86400); // +1 day

    @Nested
    @DisplayName("createDraft - 创建草稿")
    class CreateDraftTest {

        @Test
        @DisplayName("成功创建草稿状态的发布窗口")
        void should_create_draft_successfully() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", "描述", plannedRelease, "G001", now);
            
            assertEquals(ReleaseWindowStatus.DRAFT, rw.getStatus());
            assertEquals("2024-W01", rw.getName());
            assertEquals("WK-01", rw.getWindowKey());
            assertEquals("描述", rw.getDescription());
            assertEquals(plannedRelease, rw.getPlannedReleaseAt());
            assertFalse(rw.isFrozen());
            assertNotNull(rw.getId());
        }
    }

    @Nested
    @DisplayName("publish - 发布")
    class PublishTest {

        @Test
        @DisplayName("成功发布")
        void should_publish_successfully() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);

            rw.publish(now);

            assertEquals(ReleaseWindowStatus.PUBLISHED, rw.getStatus());
            assertEquals(now, rw.getPublishedAt());
        }

        @Test
        @DisplayName("已发布的窗口不能再发布")
        void should_fail_to_publish_when_already_published() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.publish(now);

            BusinessException ex = assertThrows(BusinessException.class, () -> rw.publish(now));
            assertTrue(ex.getMessage().contains("state"));
        }

        @Test
        @DisplayName("已关闭的窗口不能发布")
        void should_fail_to_publish_when_closed() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.publish(now);
            rw.close(now);

            BusinessException ex = assertThrows(BusinessException.class, () -> rw.publish(now));
            assertTrue(ex.getMessage().contains("state"));
        }
    }

    @Nested
    @DisplayName("close - 关闭")
    class CloseTest {

        @Test
        @DisplayName("成功关闭已发布的窗口")
        void should_close_published_window() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.publish(now);

            rw.close(now);

            assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());
        }

        @Test
        @DisplayName("草稿窗口不能直接关闭")
        void should_fail_to_close_draft_window() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);

            BusinessException ex = assertThrows(BusinessException.class, () -> rw.close(now));
            assertTrue(ex.getMessage().contains("state"));
        }

        @Test
        @DisplayName("已关闭的窗口再次关闭是幂等操作")
        void should_close_already_closed_idempotent() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.publish(now);
            rw.close(now);

            // 幂等操作，不抛异常
            rw.close(now);
            assertEquals(ReleaseWindowStatus.CLOSED, rw.getStatus());
        }
    }

    @Nested
    @DisplayName("freeze - 冻结")
    class FreezeTest {

        @Test
        @DisplayName("成功冻结窗口")
        void should_freeze_successfully() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.freeze(now);
            assertTrue(rw.isFrozen());
        }

        @Test
        @DisplayName("重复冻结是幂等操作")
        void should_be_idempotent_when_freeze_twice() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.freeze(now);
            rw.freeze(now.plusSeconds(5));
            assertTrue(rw.isFrozen());
        }
    }

    @Nested
    @DisplayName("unfreeze - 解冻")
    class UnfreezeTest {

        @Test
        @DisplayName("解冻成功")
        void should_unfreeze_successfully() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.freeze(now);
            rw.unfreeze(now.plusSeconds(1));
            assertFalse(rw.isFrozen());
        }

        @Test
        @DisplayName("未冻结时解冻是幂等操作")
        void should_be_idempotent_when_unfreeze_without_freeze() {
            ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, "G001", now);
            rw.unfreeze(now);
            assertFalse(rw.isFrozen());
        }
    }

    @Nested
    @DisplayName("validate - 参数校验")
    class ValidationTest {

        @Test
        @DisplayName("名称为空时抛出异常")
        void should_fail_when_name_blank() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft("WK-01", " ", null, plannedRelease, "G001", now));
            assertEquals("RW_004", ex.getCode());
        }

        @Test
        @DisplayName("窗口 key 为空时抛出异常")
        void should_fail_when_key_blank() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft(" ", "2024-W01", null, plannedRelease, "G001", now));
            assertEquals("RW_002", ex.getCode());
        }

        @Test
        @DisplayName("名称过长时抛出异常")
        void should_fail_when_name_too_long() {
            String longName = "A".repeat(129);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft("WK-01", longName, null, plannedRelease, "G001", now));
            assertEquals("RW_005", ex.getCode());
        }

        @Test
        @DisplayName("窗口 key 过长时抛出异常")
        void should_fail_when_key_too_long() {
            String longKey = "K".repeat(65);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft(longKey, "2024-W01", null, plannedRelease, "G001", now));
            assertEquals("RW_003", ex.getCode());
        }

        @Test
        @DisplayName("groupCode 为空时抛出异常")
        void should_fail_when_group_code_blank() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, " ", now));
            assertEquals("GROUP_005", ex.getCode());
        }

        @Test
        @DisplayName("groupCode 过长时抛出异常")
        void should_fail_when_group_code_too_long() {
            String longCode = "G".repeat(65);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> ReleaseWindow.createDraft("WK-01", "2024-W01", null, plannedRelease, longCode, now));
            assertEquals("GROUP_006", ex.getCode());
        }
    }
}
