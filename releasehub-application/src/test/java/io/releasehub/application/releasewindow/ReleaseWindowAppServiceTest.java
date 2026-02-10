package io.releasehub.application.releasewindow;

import io.releasehub.application.group.GroupPort;
import io.releasehub.application.release.ReleaseRunService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseWindowAppService 测试")
class ReleaseWindowAppServiceTest {

    @Mock
    private ReleaseWindowPort releaseWindowPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private ReleaseRunService releaseRunService;
    @Mock
    private GroupPort groupPort;

    private ReleaseWindowAppService releaseWindowAppService;

    @BeforeEach
    void setUp() {
        releaseWindowAppService = new ReleaseWindowAppService(releaseWindowPort, windowIterationPort, releaseRunService, groupPort);
    }

    @Nested
    @DisplayName("create 方法")
    class CreateTests {
        @Test
        @DisplayName("应生成 windowKey 并保存草稿状态")
        void shouldCreateDraftWindowWithGeneratedKey() {
            ArgumentCaptor<ReleaseWindow> captor = ArgumentCaptor.forClass(ReleaseWindow.class);
            Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
            when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
            when(groupPort.countChildren("G001")).thenReturn(0L);

            ReleaseWindowView view = releaseWindowAppService.create("Window", "Desc", Instant.now(), "G001");

            verify(releaseWindowPort).save(captor.capture());
            ReleaseWindow saved = captor.getValue();
            assertThat(saved.getStatus().name()).isEqualTo("DRAFT");
            assertThat(saved.getWindowKey()).startsWith("RW-");
            assertThat(saved.getGroupCode()).isEqualTo("G001");
            assertThat(view.getWindowKey()).startsWith("RW-");
            assertThat(view.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("group 非末端节点时创建失败")
        void shouldFailCreateWhenGroupHasChildren() {
            Instant now = Instant.now();
            Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L);
            when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
            when(groupPort.countChildren("G001")).thenReturn(1L);

            assertThatThrownBy(() -> releaseWindowAppService.create("Window", "Desc", Instant.now(), "G001"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("GROUP_008"));
        }

        @Test
        @DisplayName("group 不存在时创建失败")
        void shouldFailCreateWhenGroupNotFound() {
            when(groupPort.findByCode("G404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> releaseWindowAppService.create("Window", "Desc", Instant.now(), "G404"))
                    .isInstanceOf(NotFoundException.class)
                    .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("GROUP_002"));
        }
    }

    @Nested
    @DisplayName("publish 方法")
    class PublishTests {
        @Test
        @DisplayName("无迭代时抛异常")
        void shouldFailPublishWhenNoIterations() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1"))).thenReturn(List.of());

            assertThatThrownBy(() -> releaseWindowAppService.publish("window-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_012"));
        }

        @Test
        @DisplayName("成功后只更新状态，不触发收尾任务")
        void shouldPublishWithoutTriggeringRun() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1")))
                    .thenReturn(List.of(WindowIteration.attach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"), now, now)));

            ReleaseWindowView view = releaseWindowAppService.publish("window-1");

            verify(releaseWindowPort).save(any(ReleaseWindow.class));
            // publish 不再触发 releaseRunService
            verify(releaseRunService, never()).createReleaseRun(any(), any(), any());
            verify(releaseRunService, never()).executeRunAsync(any());
            assertThat(view.getStatus()).isEqualTo("PUBLISHED");
            assertThat(view.getPublishedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("close 方法")
    class CloseTests {
        @Test
        @DisplayName("成功后触发收尾编排任务")
        void shouldCloseAndTriggerRun() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            window.publish(now); // 先发布
            when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));

            Run run = Run.start(RunType.VERSION_UPDATE, "system", now);
            when(releaseRunService.createReleaseRun(eq("window-1"), eq("RW-1"), eq("system"))).thenReturn(run);

            ReleaseWindowView view = releaseWindowAppService.close("window-1");

            verify(releaseWindowPort).save(any(ReleaseWindow.class));
            verify(releaseRunService).createReleaseRun(eq("window-1"), eq("RW-1"), eq("system"));
            verify(releaseRunService).executeRunAsync(run.getId().value());
            assertThat(view.getStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("收尾任务创建失败时不回滚关闭状态")
        void shouldNotRollbackCloseWhenRunCreationFails() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            window.publish(now); // 先发布
            when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(releaseRunService.createReleaseRun(eq("window-1"), eq("RW-1"), eq("system")))
                    .thenThrow(new RuntimeException("boom"));

            ReleaseWindowView view = releaseWindowAppService.close("window-1");

            verify(releaseWindowPort).save(any(ReleaseWindow.class));
            verify(releaseRunService, never()).executeRunAsync(any());
            assertThat(view.getStatus()).isEqualTo("CLOSED");
        }
    }
}
