package com.liang.drugagent.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置。
 * 提供语义分析并行执行的线程池，以及通用的任务执行线程池。
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 语义分析并行执行的线程池。
     * 核心线程数 6，用于并行执行 W-P1/W-P4/W-M8/W-P2/W-P3/W-M3 语义分析器。
     */
    @Bean("semanticAnalyzerExecutor")
    public Executor semanticAnalyzerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("semantic-analyzer-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * LLM 调用专用线程池。
     * 核心线程数 10，支持多租户并发 LLM 调用。
     */
    @Bean("llmCallExecutor")
    public ExecutorService llmCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("llm-call-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}
