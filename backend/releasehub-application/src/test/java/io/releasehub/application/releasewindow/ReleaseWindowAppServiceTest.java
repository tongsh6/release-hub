package io.releasehub.application.releasewindow;

import io.releasehub.application.group.GroupPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.run.RunAppService;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.application.window.PlanAppService;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunPlanSnapshot;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseWindowAppService 测试")
class ReleaseWindowAppServiceTest {

    @Mock
    private ReleaseWindowPort releaseWindowPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private RunAppService runAppService;
    @Mock
    private GroupPort groupPort;
    @Mock
    private CodeRepositoryPort codeRepositoryPort;
    @Mock
    private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock
    private PlanAppService planAppService;

    private ReleaseWindowAppService releaseWindowAppService;

    @BeforeEach
    void setUp() {
        releaseWindowAppService = new ReleaseWindowAppService(
                releaseWindowPort, windowIterationPort, runAppService, groupPort,
                codeRepositoryPort, gitBranchAdapterFactory, planAppService);
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
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("GROUP_014"));
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
    @DisplayName("listPaged 方法")
    class ListPagedTests {
        @Test
        @DisplayName("按组织筛选时包含当前组织及子组织")
        void shouldFilterByGroupAndDescendants() {
            Instant now = Instant.now();
            Group parent = Group.rehydrate(GroupId.of("parent"), "Parent", "G001", null, now, now, 0L);
            Group child = Group.rehydrate(GroupId.of("child"), "Child", "G001001", "G001", now, now, 0L);
            Group leaf = Group.rehydrate(GroupId.of("leaf"), "Leaf", "G001001001", "G001001", now, now, 0L);
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001001001", now);
            when(groupPort.findByCode("G001")).thenReturn(Optional.of(parent));
            when(groupPort.findByParentCode("G001")).thenReturn(List.of(child));
            when(groupPort.findByParentCode("G001001")).thenReturn(List.of(leaf));
            when(groupPort.findByParentCode("G001001001")).thenReturn(List.of());
            when(releaseWindowPort.findPaged(
                    eq("Window"),
                    eq(ReleaseWindowStatus.DRAFT),
                    eq(List.of("G001", "G001001", "G001001001")),
                    eq(1),
                    eq(10)
            )).thenReturn(new PageResult<>(List.of(window), 1));

            PageResult<ReleaseWindowView> result = releaseWindowAppService.listPaged(
                    "Window", ReleaseWindowStatus.DRAFT, "G001", 1, 10);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).extracting(ReleaseWindowView::getGroupCode)
                    .containsExactly("G001001001");
        }

        @Test
        @DisplayName("组织不存在时筛选失败")
        void shouldFailWhenGroupFilterDoesNotExist() {
            when(groupPort.findByCode("G404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> releaseWindowAppService.listPaged(null, null, "G404", 1, 10))
                    .isInstanceOf(NotFoundException.class)
                    .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("GROUP_002"));
            verify(releaseWindowPort, never()).findPaged(any(), any(), any(List.class), anyInt(), anyInt());
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
            when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1"))).thenReturn(List.of());

            assertThatThrownBy(() -> releaseWindowAppService.publish("window-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_012"));
        }

        @Test
        @DisplayName("发布只更新配置，不发事件或触发编排")
        void shouldPublishWithoutTriggeringRun() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1")))
                    .thenReturn(List.of(WindowIteration.attach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"), now, now)));

            ReleaseWindowView view = releaseWindowAppService.publish("window-1");

            verify(releaseWindowPort).save(any(ReleaseWindow.class));
            verifyNoInteractions(runAppService);
            assertThat(view.getStatus()).isEqualTo("PUBLISHED");
            assertThat(view.getPublishedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTests {
        @Test
        @DisplayName("空草稿窗口可以删除")
        void shouldDeleteEmptyDraftWindow() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1"))).thenReturn(List.of());

            releaseWindowAppService.delete("window-1");

            verify(releaseWindowPort).deleteById(ReleaseWindowId.of("window-1"));
        }

        @Test
        @DisplayName("已关联迭代的草稿窗口拒绝删除")
        void shouldRejectDeleteWhenDraftHasIterations() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
            when(windowIterationPort.listByWindow(ReleaseWindowId.of("window-1")))
                    .thenReturn(List.of(WindowIteration.attach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"), now, now)));

            assertThatThrownBy(() -> releaseWindowAppService.delete("window-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_014"));
            verify(releaseWindowPort, never()).deleteById(any());
        }

        @Test
        @DisplayName("非草稿窗口拒绝删除")
        void shouldRejectDeleteWhenWindowIsNotDraft() {
            Instant now = Instant.now();
            ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
            window.publish(now);
            when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));

            assertThatThrownBy(() -> releaseWindowAppService.delete("window-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_014"));
            verify(windowIterationPort, never()).listByWindow(any());
            verify(releaseWindowPort, never()).deleteById(any());
        }
    }

    @Test
    @DisplayName("冻结与解冻读取锁内最新状态，不能用旧的已发布状态覆盖关闭")
    void freezeAndUnfreezeCannotOverwriteConcurrentClose() {
        Instant now = Instant.now();
        ReleaseWindow closed = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
        closed.publish(now);
        closed.close(now);
        when(releaseWindowPort.findByIdForUpdate(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> releaseWindowAppService.freeze("window-1")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> releaseWindowAppService.unfreeze("window-1")).isInstanceOf(BusinessException.class);

        verify(releaseWindowPort, never()).findById(any());
        verify(releaseWindowPort, never()).save(any());
        assertThat(closed.getStatus()).isEqualTo(ReleaseWindowStatus.CLOSED);
    }

    @Nested
    @DisplayName("close 方法")
    class CloseTests {
        @Test
        @DisplayName("关闭返回真实收尾Run，配置状态由同一受理事务负责")
        void shouldReturnCleanupRunFromAtomicAdmission() {
            Run run = Run.start(RunType.WINDOW_CLEANUP, "system", Instant.now());
            when(runAppService.executeCleanup("window-1", "system", true)).thenReturn(run);
            assertThat(releaseWindowAppService.close("window-1", "system", true)).isSameAs(run);
            verify(runAppService).executeCleanup("window-1", "system", true);
            verifyNoInteractions(releaseWindowPort);
        }

        @Test
        @DisplayName("受理或执行失败不能吞掉异常伪装关闭成功")
        void shouldPropagateAdmissionFailureWithoutIndependentWindowSave() {
            when(runAppService.executeCleanup("window-1", "system", false))
                    .thenThrow(new IllegalStateException("admission failed"));
            assertThatThrownBy(() -> releaseWindowAppService.close("window-1", "system", false))
                    .isInstanceOf(IllegalStateException.class).hasMessage("admission failed");
            verifyNoInteractions(releaseWindowPort);
        }
    }

    @Nested
    @DisplayName("getBranchStatus 方法")
    class GetBranchStatusTests {

        @Test
        @DisplayName("窗口存在迭代和仓库时返回分支状态")
        void shouldReturnBranchStatusForReposInWindow() {
            Instant now = Instant.now();
            String windowId = "window-1";
            String windowKey = "RW-20260101-ABCD";
            String iterKey = "ITER-1";
            String repoId = "repo-1";

            var item = new RunPlanSnapshot.Item("item-1", 1, repoId, "my-repo", iterKey, "GITLAB", "main",
                    "feature/" + iterKey, "AUTO", "release/" + windowKey, null, null, null, List.of(), List.of(iterKey), null, false, null);
            when(planAppService.getSnapshotByWindow(windowId)).thenReturn(new RunPlanSnapshot(
                    windowKey, "G001", "PUBLISHED", false, true, List.of(item)));

            CodeRepository repo = CodeRepository.rehydrate(
                    RepoId.of(repoId), "my-repo", "https://gitlab.example/org/repo.git",
                    "main", "G001", null, GitProvider.GITLAB, "token", false,
                    0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
            when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(repo));

            GitBranchPort mockAdapter = org.mockito.Mockito.mock(GitBranchPort.class);
            when(gitBranchAdapterFactory.getAdapter(GitProvider.GITLAB)).thenReturn(mockAdapter);
            when(mockAdapter.getBranchStatus(any(), any(), eq("feature/" + iterKey)))
                    .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
            when(mockAdapter.getBranchStatus(any(), any(), eq("release/" + windowKey)))
                    .thenReturn(GitBranchPort.BranchStatus.missing());

            BranchStatusView result = releaseWindowAppService.getBranchStatus(windowId);

            assertThat(result.windowId()).isEqualTo(windowId);
            assertThat(result.windowKey()).isEqualTo(windowKey);
            assertThat(result.repos()).hasSize(1);
            BranchStatusView.RepoBranchStatus repoStatus = result.repos().get(0);
            assertThat(repoStatus.repoId()).isEqualTo(repoId);
            assertThat(repoStatus.featureBranch().exists()).isTrue();
            assertThat(repoStatus.featureBranch().latestCommit()).isEqualTo("abc123");
            assertThat(repoStatus.releaseBranch().exists()).isFalse();
            assertThat(BranchStatusView.ReleaseBranchInfo.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("mergeStatus");
        }

        @Test
        @DisplayName("无迭代时返回空仓库列表")
        void shouldReturnEmptyReposWhenNoIterations() {
            String windowId = "window-1";
            when(planAppService.getSnapshotByWindow(windowId)).thenReturn(new RunPlanSnapshot(
                    "RW-1", "G001", "DRAFT", false, true, List.of()));

            BranchStatusView result = releaseWindowAppService.getBranchStatus(windowId);

            assertThat(result.repos()).isEmpty();
        }
    }
}