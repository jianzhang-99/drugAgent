package com.liang.drugagent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础组件配置类
 */
@Configuration
public class AiConfiguration {

    /**
     * 配置对话记忆存储 (初始使用内存存储)
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}
