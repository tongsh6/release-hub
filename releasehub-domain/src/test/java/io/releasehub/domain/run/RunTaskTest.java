package io.releasehub.domain.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RunTask 领域实体单元测试")
class RunTaskTest {

    private static final Instant NOW = Instant.now();
    private static final RunId RUN_ID = RunId.generate(RunType.VERSION_UPDATE, NOW);

    @Nested
    @DisplayName("create - 创建任务")
    class CreateTest {

        @Test
        @DisplayName("创建任务时状态为 PENDING")
        void shouldCreateWithPendingStatus() {
            RunTask task = RunTask.create(
                    RUN_ID, RunTaskType.CLOSE_ITERATION, 1,
                    TargetType.ITERATION, "iter-001", 3, NOW
            );

            assertThat(task.getStatus()).isEqualTo(RunTaskStatus.PENDING);
            assertThat(task.getRetryCount()).isEqualTo(0);
            assertThat(task.getMaxRetries()).isEqualTo(3);
            assertThat(task.getStartedAt()).isNull();
            assertThat(task.getFinishedAt()).isNull();
            assertThat(task.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("创建任务时设置基本属性")
        void shouldSetBasicProperties() {
            RunTask task = RunTask.create(
                    RUN_ID, RunTaskType.MERGE_RELEASE_TO_MASTER, 2,
                    TargetType.REPOSITORY, "repo-001", 5, NOW
            );

            assertThat(task.getRunId()).isEqualTo(RUN_ID);
            assertThat(task.getTaskType()).isEqualTo(RunTaskType.MERGE_RELEASE_TO_MASTER);
            assertThat(task.getTaskOrder()).isEqualTo(2);
            assertThat(task.getTargetType()).isEqualTo(TargetType.REPOSITORY);
            assertThat(task.getTargetId()).isEqualTo("repo-001");
        }
    }

    @Nested
    @DisplayName("start - 开始执行")
    class StartTest {

        @Test
        @DisplayName("开始执行时状态变为 RUNNING")
        void shouldChangeStatusToRunning() {
            RunTask task = createTask();
            Instant startTime = Instant.now();

            task.start(startTime);

            assertThat(task.getStatus()).isEqualTo(RunTaskStatus.RUNNING);
            assertThat(task.getStartedAt()).isEqualTo(startTime);
        }
    }

    @Nested
    @DisplayName("markCompleted - 标记完成")
    class MarkCompletedTest {

        @Test
        @DisplayName("完成时状态变为 COMPLETED")
        void shouldChangeStatusToCompleted() {
            RunTask task = createTask();
            task.start(NOW);
            Instant finishTime = Instant.now();

            task.markCompleted(finishTime);

            assertThat(task.getStatus()).isEqualTo(RunTaskStatus.COMPLETED);
            assertThat(task.getFinishedAt()).isEqualTo(finishTime);
            assertThat(task.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("markFailed - 标记失败")
    class MarkFailedTest {

        @Test
        @DisplayName("失败时状态变为 FAILED 并记录错误信息")
        void shouldChangeStatusToFailedWithErrorMessage() {
            RunTask task = createTask();
            task.start(NOW);
            Instant finishTime = Instant.now();

            task.markFailed("Connection timeout", finishTime);

            assertThat(task.getStatus()).isEqualTo(RunTaskStatus.FAILED);
            assertThat(task.getErrorMessage()).isEqualTo("Connection timeout");
            assertThat(task.getFinishedAt()).isEqualTo(finishTime);
            assertThat(task.isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("markSkipped - 标记跳过")
    class MarkSkippedTest {

        @Test
        @DisplayName("跳过时状态变为 SKIPPED")
        void shouldChangeStatusToSkipped() {
            RunTask task = createTask();
            Instant skipTime = Instant.now();

            task.markSkipped(skipTime);

            assertThat(task.getStatus()).isEqualTo(RunTaskStatus.SKIPPED);
            assertThat(task.getFinishedAt()).isEqualTo(skipTime);
        }
    }

    @Nested
    @DisplayName("retry - 重试机制")
    class RetryTest {

        @Test
        @DisplayName("未超过最大重试次数时可重试")
        void shouldAllowRetryWhenUnderMaxRetries() {
            RunTask task = RunTask.create(
                    RUN_ID, RunTaskType.CREATE_TAG, 1,
                    TargetType.REPOSITORY, "repo-001", 3, NOW
            );

            assertThat(task.canRetry()).isTrue();
            
            task.incrementRetry();
            assertThat(task.getRetryCount()).isEqualTo(1);
            assertThat(task.canRetry()).isTrue();

            task.incrementRetry();
            assertThat(task.getRetryCount()).isEqualTo(2);
            assertThat(task.canRetry()).isTrue();

            task.incrementRetry();
            assertThat(task.getRetryCount()).isEqualTo(3);
            assertThat(task.canRetry()).isFalse();
        }

        @Test
        @DisplayName("达到最大重试次数后不可重试")
        void shouldNotAllowRetryWhenMaxRetriesReached() {
            RunTask task = RunTask.create(
                    RUN_ID, RunTaskType.TRIGGER_CI_BUILD, 1,
                    TargetType.REPOSITORY, "repo-001", 2, NOW
            );

            task.incrementRetry();
            task.incrementRetry();

            assertThat(task.canRetry()).isFalse();
        }
    }

    @Nested
    @DisplayName("status checks - 状态检查")
    class StatusChecksTest {

        @Test
        @DisplayName("isPending 正确返回")
        void shouldReturnCorrectPendingStatus() {
            RunTask task = createTask();
            assertThat(task.isPending()).isTrue();
            assertThat(task.isCompleted()).isFalse();
            assertThat(task.isFailed()).isFalse();
        }

        @Test
        @DisplayName("isCompleted 正确返回")
        void shouldReturnCorrectCompletedStatus() {
            RunTask task = createTask();
            task.markCompleted(NOW);
            
            assertThat(task.isPending()).isFalse();
            assertThat(task.isCompleted()).isTrue();
            assertThat(task.isFailed()).isFalse();
        }

        @Test
        @DisplayName("isFailed 正确返回")
        void shouldReturnCorrectFailedStatus() {
            RunTask task = createTask();
            task.markFailed("Error", NOW);
            
            assertThat(task.isPending()).isFalse();
            assertThat(task.isCompleted()).isFalse();
            assertThat(task.isFailed()).isTrue();
        }
    }

    private RunTask createTask() {
        return RunTask.create(
                RUN_ID, RunTaskType.CLOSE_ITERATION, 1,
                TargetType.ITERATION, "iter-001", 3, NOW
        );
    }
}
