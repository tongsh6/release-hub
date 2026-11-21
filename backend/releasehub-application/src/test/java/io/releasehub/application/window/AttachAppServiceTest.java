package io.releasehub.application.window;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("窗口迭代绑定只改变配置")
class AttachAppServiceTest {
    @Mock private ReleaseWindowPort releaseWindowPort;
    @Mock private IterationPort iterationPort;
    @Mock private WindowIterationPort windowIterationPort;

    private AttachAppService service;
    private final Instant now = Instant.parse("2026-11-21T04:00:00Z");
    private final ReleaseWindowId windowId = ReleaseWindowId.of("window-1");
    private final RepoId sharedRepo = RepoId.of("repo-shared");

    @BeforeEach
    void setUp() {
        service = new AttachAppService(releaseWindowPort, iterationPort, windowIterationPort,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void attachOnlyBindsIterationsEvenWhenTheirRepositoryOverlaps() {
        allowDraftWindow();
        registerIteration("ITER-1", "G001");
        registerIteration("ITER-2", "G001");
        stubAttach("ITER-1");
        stubAttach("ITER-2");

        assertThat(service.attach(windowId.value(), List.of("ITER-1", "ITER-2"))).hasSize(2);

        verify(windowIterationPort).attach(eq(windowId), eq(IterationKey.of("ITER-1")), any());
        verify(windowIterationPort).attach(eq(windowId), eq(IterationKey.of("ITER-2")), any());
        verify(windowIterationPort, never()).updateReleaseBranch(any(), any(), any(), any());
        verify(windowIterationPort, never()).updateLastMergeAt(any(), any(), any());
    }

    @Test
    void detachOnlyRemovesTheSelectedBindingWithoutArchivingSharedBranches() {
        allowDraftWindow();

        service.detach(windowId.value(), "ITER-1");

        verify(windowIterationPort).detach(windowId, IterationKey.of("ITER-1"));
        verifyNoInteractions(iterationPort);
        verify(windowIterationPort, never()).detach(windowId, IterationKey.of("ITER-2"));
        verify(windowIterationPort, never()).detach(ReleaseWindowId.of("window-2"), IterationKey.of("ITER-1"));
    }

    @Test
    void repeatedAttachKeepsTheOriginalAttachmentAndOrder() {
        allowDraftWindow();
        registerIteration("ITER-1", "G001");
        Instant attachedAt = now.minusSeconds(3600);
        WindowIteration existing = WindowIteration.attach(windowId, IterationKey.of("ITER-1"), attachedAt, attachedAt);
        when(windowIterationPort.findByWindowIdAndIterationKey(windowId, IterationKey.of("ITER-1")))
                .thenReturn(Optional.of(existing));

        assertThat(service.attach(windowId.value(), List.of("ITER-1"))).containsExactly(existing);

        verify(windowIterationPort, never()).attach(any(), any(), any());
    }

    @Test
    void repeatedKeysInOneRequestCreateOneBinding() {
        allowDraftWindow();
        registerIteration("ITER-1", "G001");
        stubAttach("ITER-1");

        assertThat(service.attach(windowId.value(), List.of("ITER-1", "ITER-1"))).hasSize(1);

        verify(windowIterationPort, times(1)).attach(eq(windowId), eq(IterationKey.of("ITER-1")), any());
    }

    @Test
    void batchGroupValidationFinishesBeforeAnyBindingIsWritten() {
        allowDraftWindow();
        registerIteration("ITER-1", "G001");
        registerIteration("ITER-2", "G002");

        assertThatThrownBy(() -> service.attach(windowId.value(), List.of("ITER-1", "ITER-2")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_013"));

        verify(windowIterationPort, never()).attach(any(), any(), any());
    }

    @Test
    void frozenWindowRejectsAttachBeforeChangingBindings() {
        ReleaseWindow window = window(ReleaseWindowStatus.DRAFT);
        window.freeze(now);
        when(releaseWindowPort.findById(windowId)).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.attach(windowId.value(), List.of("ITER-1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_006"));
        verifyNoInteractions(windowIterationPort, iterationPort);
    }

    @Test
    void frozenWindowRejectsDetachBeforeChangingBindings() {
        ReleaseWindow window = window(ReleaseWindowStatus.DRAFT);
        window.freeze(now);
        when(releaseWindowPort.findById(windowId)).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.detach(windowId.value(), "ITER-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_006"));
        verifyNoInteractions(windowIterationPort, iterationPort);
    }

    @Test
    void publishedAndClosedWindowsRejectAttachAndDetach() {
        for (ReleaseWindowStatus status : List.of(ReleaseWindowStatus.PUBLISHED, ReleaseWindowStatus.CLOSED)) {
            when(releaseWindowPort.findById(windowId)).thenReturn(Optional.of(window(status)));
            assertThatThrownBy(() -> service.attach(windowId.value(), List.of("ITER-1")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_009"));
            assertThatThrownBy(() -> service.detach(windowId.value(), "ITER-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_009"));
        }
        verifyNoInteractions(windowIterationPort, iterationPort);
    }

    private void allowDraftWindow() {
        when(releaseWindowPort.findById(windowId)).thenReturn(Optional.of(window(ReleaseWindowStatus.DRAFT)));
    }

    private ReleaseWindow window(ReleaseWindowStatus status) {
        return ReleaseWindow.rehydrate(windowId, "RW-1", "窗口", null, now,
                "G001", status, now, now, false, null);
    }

    private void registerIteration(String key, String group) {
        when(iterationPort.findByKey(IterationKey.of(key))).thenReturn(Optional.of(iteration(key, group)));
    }

    private Iteration iteration(String key, String group) {
        return Iteration.rehydrate(IterationKey.of(key), key, null, null, group,
                Set.of(sharedRepo), IterationStatus.ACTIVE, now, now);
    }

    private void stubAttach(String key) {
        when(windowIterationPort.attach(eq(windowId), eq(IterationKey.of(key)), any()))
                .thenAnswer(invocation -> WindowIteration.attach(windowId, IterationKey.of(key),
                        invocation.getArgument(2), invocation.getArgument(2)));
    }
}