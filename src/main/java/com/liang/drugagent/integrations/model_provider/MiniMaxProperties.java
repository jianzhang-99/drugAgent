package com.liang.drugagent.integrations.model_provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MiniMax模型配置属性类
 *
 * 对应application.yml中的minimax配置节点
 *
 * @author liangjiajian
 */
@Component
@ConfigurationProperties(prefix = "minimax")
public class MiniMaxProperties {

    /**
     * 是否启用MiniMax provider
     */
    private boolean enabled = false;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * MiniMax API根地址
     */
    private String baseUrl = "https://api.minimaxi.com";

    /**
     * 模型名称
     */
    private String model = "MiniMax-Text-01";

    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 60000;

    /**
     * 最大token数
     */
    private int maxTokens = 8192;

    /**
     * 温度参数（创造性）
     */
    private float temperature = 0.7f;

    /**
     * 流式输出开关
     */
    private boolean stream = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}
