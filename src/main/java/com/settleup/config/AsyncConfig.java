package com.settleup.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded pool for work that must not block the HTTP request: notifications and CSV exports.
 *
 * <p>Both the pool and the queue are deliberately bounded. An unbounded queue is the classic way to
 * turn a slow downstream into an out-of-memory crash, because tasks pile up faster than they drain.
 * With a bounded queue we hit the rejection policy instead, and {@code CallerRunsPolicy} pushes the
 * work back onto the submitting thread, which naturally slows producers down rather than dropping
 * anyone's notification on the floor.
 */
@Configuration
@Slf4j
public class AsyncConfig {

    @Bean(name = "backgroundExecutor")
    public Executor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("settleup-bg-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight notifications finish on shutdown instead of killing them mid-send.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
