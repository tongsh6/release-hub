package io.releasehub.bootstrap.config;

import io.releasehub.application.run.RunAppService;
import io.releasehub.application.run.RunPort;
import io.releasehub.infrastructure.run.RunTaskWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 窗口任务唯一后台装配；关闭只停止派发，不回退请求线程同步执行。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "releasehub.run.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncConfig {
    @Bean(destroyMethod = "close")
    public RunTaskWorker runTaskWorker(RunPort runs, RunAppService service,
            @Value("${releasehub.run.worker.parallelism:2}") int parallelism,
            @Value("${releasehub.run.worker.queue-capacity:2}") int queueCapacity,
            @Value("${releasehub.run.worker.scan-batch-size:32}") int batchSize) {
        return new RunTaskWorker(runs, service, parallelism, queueCapacity, batchSize);
    }
}
