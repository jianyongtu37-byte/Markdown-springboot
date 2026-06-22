package com.nineone.markdown.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * 异步任务与定时任务线程池配置
 *
 * 线程池职责划分：
 * - aiExecutor:      AI 摘要生成（IO 密集型，HTTP 调用 DeepSeek API，最长 60s）
 * - aiTaskExecutor:  RAG 向量索引同步（IO 密集型，HTTP 调用 Python RAG 服务）
 * - esExecutor:      Elasticsearch 索引操作（IO 密集型，但比 AI 调用快得多）
 * - quickExecutor:   轻量级 DB 操作（浏览量更新等，毫秒级）
 * - taskExecutor:    通用任务（通知推送、邮件发送）
 * - schedulerPool:   @Scheduled 定时任务（浏览量同步、自动备份等）
 */
@Configuration
@EnableAsync
public class AsyncConfig implements SchedulingConfigurer {

    /**
     * AI 摘要生成专用执行器
     * 特点：IO 密集型，线程阻塞在 HTTP 调用上（DeepSeek API，最长 60s）
     * 配置较多线程以支撑并发 AI 请求
     */
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpu = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cpu * 2);
        executor.setMaxPoolSize(cpu * 4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * RAG 向量索引同步专用执行器
     * 特点：IO 密集型，HTTP 调用 Python RAG 服务进行向量索引同步，耗时中等
     * 与 aiExecutor 隔离，避免摘要生成阻塞索引同步
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("aitask-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Elasticsearch 索引操作专用执行器
     * 特点：IO 密集型但耗时较短（通常毫秒到秒级）
     */
    @Bean(name = "esExecutor")
    public Executor esExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("es-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 轻量级任务执行器（浏览量更新等快速 DB 操作）
     * 特点：单条 SQL，毫秒级完成
     */
    @Bean(name = "quickExecutor")
    public Executor quickExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("quick-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务执行器（通知推送、邮件发送）
     * Spring 默认使用 taskExecutor 作为 @Async 无指定 executor 时的兜底
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 代理专用执行器（RAG 流式问答转发）
     * 替代 RAGController 中的无界 CachedThreadPool，防止高并发时线程爆炸
     */
    @Bean(name = "sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    /**
     * 定时任务调度线程池
     * 默认 Spring 只分配 1 个线程给所有 @Scheduled 任务，
     * 如果备份任务耗时长会阻塞浏览量同步等其他定时任务。
     * 这里配置 3 个线程，让定时任务可以并发执行。
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(Executors.newScheduledThreadPool(3));
    }
}
