package com.liang.drugagent.shared.llm;

/**
 * LLM Provider 枚举类
 * 支持多模型服务商切换
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public enum LlmProviderType {

    /**
     * 阿里云百炼平台（通义千问）
     */
    DASHSCOPE("阿里云百炼", "dashscope"),

    /**
     * MiniMax AI平台
     */
    MINIMAX("MiniMax", "minimax");

    private final String displayName;
    private final String configKey;

    LlmProviderType(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取配置键名
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 根据配置键名查找枚举
     */
    public static LlmProviderType fromConfigKey(String configKey) {
        for (LlmProviderType type : values()) {
            if (type.configKey.equalsIgnoreCase(configKey)) {
                return type;
            }
        }
        return MINIMAX;
    }
}
