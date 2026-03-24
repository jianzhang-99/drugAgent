package com.liang.drugagent.app.config;

import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础组件配置类
 *
 * @author liangjiajian
 */
@Configuration
public class LLMConfig {

    /**
     * 配置对话记忆存储 (使用内存存储)
     * 注意：生产环境建议使用 JDBC 或 Redis 等持久化方案
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}