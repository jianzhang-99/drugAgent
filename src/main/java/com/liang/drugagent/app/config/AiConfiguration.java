package com.liang.drugagent.app.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * AI 基础组件配置类
 *
 *
 * @author liangjiajian
 */
@Configuration
public class AiConfiguration {

    /**
     * 配置对话记忆存储 (使用JDBC持久化到MySQL)
     */
    @Bean
    public ChatMemory chatMemory(DataSource dataSource) {
        return new JdbcChatMemory(dataSource);
    }
}
