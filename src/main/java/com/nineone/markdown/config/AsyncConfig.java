package com.nineone.markdown.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;


/**
 * 异步任务配置类
 * 简化配置，保留两个线程池：AI专用和通用任务
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * AI相关异步任务执行器
     * 用于处理AI摘要生成等耗时操作
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核心数
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数：核心线程数的2倍
        executor.setMaxPoolSize(corePoolSize * 2);
        // 队列容量：防止内存溢出
        executor.setQueueCapacity(100);
        // 线程空闲时间：60秒
        executor.setKeepAliveSeconds(60);
        // 线程名前缀
        executor.setThreadNamePrefix("ai-async-");
        // 拒绝策略：调用者运行（由调用线程执行任务）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务执行器
     * 用于处理其他所有异步任务，合并了原来的commonTaskExecutor和dbTaskExecutor
     */
    @Bean(name = "taskExecutor")  // Spring默认使用taskExecutor
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 适中的线程池大小，适合大多数异步任务
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
