package io.releasehub.application.release;

import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RunTaskExecutorRegistry 测试")
class RunTaskExecutorRegistryTest {

    @Test
    @DisplayName("注册并获取执行器")
    void shouldRegisterAndGetExecutor() {
        // 创建模拟执行器
        AbstractRunTaskExecutor closeIterationExecutor = new AbstractRunTaskExecutor() {
            @Override
            public RunTaskType getTaskType() {
                return RunTaskType.CLOSE_ITERATION;
            }

            @Override
            public void execute(RunTask task) {
                // 空实现
            }
        };

        AbstractRunTaskExecutor createTagExecutor = new AbstractRunTaskExecutor() {
            @Override
            public RunTaskType getTaskType() {
                return RunTaskType.CREATE_TAG;
            }

            @Override
            public void execute(RunTask task) {
                // 空实现
            }
        };

        // 注册执行器
        RunTaskExecutorRegistry registry = new RunTaskExecutorRegistry(
                List.of(closeIterationExecutor, createTagExecutor)
        );

        // 验证
        assertThat(registry.getExecutor(RunTaskType.CLOSE_ITERATION)).isEqualTo(closeIterationExecutor);
        assertThat(registry.getExecutor(RunTaskType.CREATE_TAG)).isEqualTo(createTagExecutor);
    }

    @Test
    @DisplayName("未注册类型返回 null")
    void shouldReturnNullForUnregisteredType() {
        RunTaskExecutorRegistry registry = new RunTaskExecutorRegistry(List.of());

        assertThat(registry.getExecutor(RunTaskType.TRIGGER_CI_BUILD)).isNull();
    }

    @Test
    @DisplayName("注册所有任务类型")
    void shouldRegisterAllTaskTypes() {
        List<AbstractRunTaskExecutor> executors = List.of(
                createMockExecutor(RunTaskType.CLOSE_ITERATION),
                createMockExecutor(RunTaskType.ARCHIVE_FEATURE_BRANCH),
                createMockExecutor(RunTaskType.UPDATE_POM_VERSION),
                createMockExecutor(RunTaskType.MERGE_RELEASE_TO_MASTER),
                createMockExecutor(RunTaskType.CREATE_TAG),
                createMockExecutor(RunTaskType.TRIGGER_CI_BUILD),
                createMockExecutor(RunTaskType.CREATE_RELEASE_BRANCH),
                createMockExecutor(RunTaskType.MERGE_FEATURE_TO_RELEASE)
        );

        RunTaskExecutorRegistry registry = new RunTaskExecutorRegistry(executors);

        // 验证所有类型都有执行器
        for (RunTaskType taskType : RunTaskType.values()) {
            assertThat(registry.getExecutor(taskType))
                    .as("Executor for %s should be registered", taskType)
                    .isNotNull();
        }
    }

    private AbstractRunTaskExecutor createMockExecutor(RunTaskType taskType) {
        return new AbstractRunTaskExecutor() {
            @Override
            public RunTaskType getTaskType() {
                return taskType;
            }

            @Override
            public void execute(RunTask task) {
                // 空实现
            }
        };
    }
}
