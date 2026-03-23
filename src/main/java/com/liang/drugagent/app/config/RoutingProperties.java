package com.liang.drugagent.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 路由配置属性类。
 *
 * <p>从 application.yml 读取路由相关配置，包括：</p>
 * <ul>
 *   <li>百炼路由开关 (llm-enabled)</li>
 *   <li>百炼调用超时 (llm-timeout)</li>
 *   <li>置信度采纳阈值 (confidence-threshold)</li>
 *   <li>置信度回退阈值 (confidence-low-threshold)</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.routing")
public class RoutingProperties {

    /**
     * 是否启用百炼路由。
     * 当设置为 false 时，直接使用规则路由或 UNKNOWN 兜底。
     */
    private boolean llmEnabled = true;

    /**
     * 百炼模型调用超时时间（毫秒）。
     */
    private int llmTimeout = 10000;

    /**
     * 置信度采纳阈值。
     * 当 LLM 返回的置信度 >= 此值时，直接采纳 LLM 决策。
     */
    private double confidenceThreshold = 0.75;

    /**
     * 置信度回退阈值。
     * 当 LLM 返回的置信度 < 此值时，回退到 UNKNOWN。
     */
    private double confidenceLowThreshold = 0.5;
}
