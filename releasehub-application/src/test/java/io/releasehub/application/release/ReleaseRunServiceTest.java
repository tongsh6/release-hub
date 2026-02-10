package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.RunTaskPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunId;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskId;
import io.releasehub.domain.run.RunTaskStatus;
import io.releasehub.domain.run.RunTaskType;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.run.TargetType;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseRunService 测试")
class ReleaseRunServiceTest {

    @Mock
    private RunPort runPort;
    @Mock
    private RunTaskPort runTaskPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private IterationAppService iterationAppService;
    @Mock
    private RunTaskExecutorRegistry executorRegistry;
    @Mock
    private MessageSource messageSource;

    @Captor
    private ArgumentCaptor<Run> runCaptor;
    @Captor
    private ArgumentCaptor<RunTask> taskCaptor;

    private ReleaseRunService releaseRunService;

    @BeforeEach
    void setUp() {
        releaseRunService = new ReleaseRunService(
                runPort, runTaskPort, windowIterationPort,
                iterationAppService, executorRegistry, messageSource
        );
    }

    @Nested
    @DisplayName("createReleaseRun - 创建发布运行")
    class CreateReleaseRunTest {

        @Test
        @DisplayName("成功创建运行及任务")
        void shouldCreateRunWithTasks() {
            String windowId = "window-001";
            String windowKey = "v1.0.0";
            String operator = "admin";

            // 准备迭代数据
            IterationKey iterKey = IterationKey.of("iter-001");
            Instant now = Instant.now();
            WindowIteration wi = WindowIteration.attach(
                    ReleaseWindowId.of(windowId), iterKey, now, now
            );
            when(windowIterationPort.listByWindow(any(ReleaseWindowId.class)))
                    .thenReturn(List.of(wi));

            // 准备迭代详情
            Iteration iteration = Iteration.rehydrate(
                    iterKey, "迭代1", "描述", null, "G001",
                    Set.of(RepoId.of("repo-001")), IterationStatus.ACTIVE,
                    Instant.now(), Instant.now()
            );
            when(iterationAppService.get("iter-001")).thenReturn(iteration);

            // 执行
            Run run = releaseRunService.createReleaseRun(windowId, windowKey, operator);

            // 验证
            assertThat(run).isNotNull();
            verify(runPort).save(runCaptor.capture());
            Run savedRun = runCaptor.getValue();
            assertThat(savedRun.getRunType()).isEqualTo(RunType.VERSION_UPDATE);
            assertThat(savedRun.getOperator()).isEqualTo(operator);

            // 验证任务创建（1个迭代 + 1个仓库的5种任务 = 1 + 5 = 6个任务）
            verify(runTaskPort, times(6)).save(taskCaptor.capture());
            List<RunTask> savedTasks = taskCaptor.getAllValues();

            // 验证任务类型
            assertThat(savedTasks).extracting(RunTask::getTaskType)
                                  .containsExactly(
                                          RunTaskType.CLOSE_ITERATION,
                                          RunTaskType.ARCHIVE_FEATURE_BRANCH,
                                          RunTaskType.UPDATE_POM_VERSION,
                                          RunTaskType.MERGE_RELEASE_TO_MASTER,
                                          RunTaskType.CREATE_TAG,
                                          RunTaskType.TRIGGER_CI_BUILD
                                  );
        }

        @Test
        @DisplayName("无迭代时只创建运行记录")
        void shouldCreateRunWithoutTasksWhenNoIterations() {
            String windowId = "window-001";
            String windowKey = "v1.0.0";

            when(windowIterationPort.listByWindow(any(ReleaseWindowId.class)))
                    .thenReturn(List.of());

            Run run = releaseRunService.createReleaseRun(windowId, windowKey, "admin");

            assertThat(run).isNotNull();
            verify(runPort).save(any(Run.class));
            verify(runTaskPort, never()).save(any(RunTask.class));
        }

        @Test
        @DisplayName("多迭代多仓库时正确生成任务")
        void shouldGenerateTasksForMultipleIterationsAndRepos() {
            String windowId = "window-001";
            String windowKey = "v2.0.0";

            // 2个迭代
            IterationKey iterKey1 = IterationKey.of("iter-001");
            IterationKey iterKey2 = IterationKey.of("iter-002");

            Instant now = Instant.now();
            WindowIteration wi1 = WindowIteration.attach(
                    ReleaseWindowId.of(windowId), iterKey1, now, now);
            WindowIteration wi2 = WindowIteration.attach(
                    ReleaseWindowId.of(windowId), iterKey2, now, now);

            when(windowIterationPort.listByWindow(any(ReleaseWindowId.class)))
                    .thenReturn(List.of(wi1, wi2));

            // 迭代1有2个仓库
            Iteration iteration1 = Iteration.rehydrate(
                    iterKey1, "迭代1", null, null, "G001",
                    Set.of(RepoId.of("repo-001"), RepoId.of("repo-002")), IterationStatus.ACTIVE,
                    Instant.now(), Instant.now()
            );
            // 迭代2有1个仓库
            Iteration iteration2 = Iteration.rehydrate(
                    iterKey2, "迭代2", null, null, "G001",
                    Set.of(RepoId.of("repo-003")), IterationStatus.ACTIVE,
                    Instant.now(), Instant.now()
            );

            when(iterationAppService.get("iter-001")).thenReturn(iteration1);
            when(iterationAppService.get("iter-002")).thenReturn(iteration2);

            releaseRunService.createReleaseRun(windowId, windowKey, "admin");

            // 2个关闭迭代任务 + (2+1)个仓库 * 5种任务 = 2 + 15 = 17个任务
            verify(runTaskPort, times(17)).save(any(RunTask.class));
        }
    }

    @Nested
    @DisplayName("retryTask - 重试任务")
    class RetryTaskTest {

        @Test
        @DisplayName("成功重试失败任务")
        void shouldRetryFailedTask() {
            String taskId = "task-001";
            Instant now = Instant.now();
            RunTask failedTask = RunTask.rehydrate(
                    RunTaskId.of(taskId), RunId.generate(RunType.VERSION_UPDATE, now), RunTaskType.CREATE_TAG, 1,
                    TargetType.REPOSITORY, "repo-001", RunTaskStatus.FAILED,
                    3, 3, "Error", now, now, now, now
            );

            when(runTaskPort.findById(taskId)).thenReturn(Optional.of(failedTask));

            RunTask result = releaseRunService.retryTask(taskId);

            assertThat(result.getStatus()).isEqualTo(RunTaskStatus.PENDING);
            assertThat(result.getRetryCount()).isEqualTo(0);
            assertThat(result.getErrorMessage()).isNull();
            // retryTask 会保存一次，然后异步执行可能保存更多次
            verify(runTaskPort, atLeastOnce()).save(any(RunTask.class));
        }

        @Test
        @DisplayName("非失败任务不能重试")
        void shouldNotRetryNonFailedTask() {
            String taskId = "task-001";
            Instant now = Instant.now();
            RunTask completedTask = RunTask.rehydrate(
                    RunTaskId.of(taskId), RunId.generate(RunType.VERSION_UPDATE, now), RunTaskType.CREATE_TAG, 1,
                    TargetType.REPOSITORY, "repo-001", RunTaskStatus.COMPLETED,
                    0, 3, null, now, now, now, now
            );

            when(runTaskPort.findById(taskId)).thenReturn(Optional.of(completedTask));

            assertThatThrownBy(() -> releaseRunService.retryTask(taskId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BaseException base = (BaseException) ex;
                        assertThat(base.getCode()).isEqualTo("RUN_005");
                    });
        }

        @Test
        @DisplayName("任务不存在时抛出异常")
        void shouldThrowWhenTaskNotFound() {
            when(runTaskPort.findById("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> releaseRunService.retryTask("unknown"))
                    .isInstanceOf(NotFoundException.class)
                    .satisfies(ex -> {
                        BaseException base = (BaseException) ex;
                        assertThat(base.getCode()).isEqualTo("RUN_004");
                    });
        }
    }

    @Nested
    @DisplayName("getRunTasks - 获取运行任务")
    class GetRunTasksTest {

        @Test
        @DisplayName("返回运行的所有任务")
        void shouldReturnAllTasksForRun() {
            String runId = "run-001";
            RunId runIdObj = RunId.of(runId);

            List<RunTask> tasks = List.of(
                    RunTask.create(runIdObj, RunTaskType.CLOSE_ITERATION, 1,
                            TargetType.ITERATION, "iter-001", 3, Instant.now()),
                    RunTask.create(runIdObj, RunTaskType.CREATE_TAG, 2,
                            TargetType.REPOSITORY, "repo-001", 3, Instant.now())
            );

            when(runTaskPort.findByRunId(runId)).thenReturn(tasks);

            List<RunTask> result = releaseRunService.getRunTasks(runId);

            assertThat(result).hasSize(2);
            verify(runTaskPort).findByRunId(runId);
        }
    }
}
