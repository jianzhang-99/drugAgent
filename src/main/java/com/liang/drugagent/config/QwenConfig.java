package com.liang.drugagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 千问(DashScope) 配置类
 * 从 application.yml 中读取 api-key，供 Service 层注入使用
 */
@Configuration
public class QwenConfig {

    @Value("${aliyun.dashscope.api-key}")
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }
}
