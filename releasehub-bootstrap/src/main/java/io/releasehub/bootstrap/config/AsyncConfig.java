package io.releasehub.bootstrap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步任务配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 使用 Spring 默认的 SimpleAsyncTaskExecutor
    // 可以根据需要自定义线程池
}
