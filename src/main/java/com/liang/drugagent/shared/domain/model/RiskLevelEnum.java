package com.liang.drugagent.shared.domain.model;

/**
 * 风险等级枚举
 * 用于标识任务和风险项的风险等级
 *
 * @author drug-agent-team
 */
public enum RiskLevelEnum {

    /**
     * 高风险 - 需要立即处理
     */
    HIGH("HIGH", "高风险", 1, "#EF4444"),

    /**
     * 中风险 - 需要关注
     */
    MEDIUM("MEDIUM", "中风险", 2, "#F59E0B"),

    /**
     * 低风险 - 可接受
     */
    LOW("LOW", "低风险", 3, "#10B981"),

    /**
     * 未知 - 待评估
     */
    UNKNOWN("UNKNOWN", "待评估", 4, "#6B7280");

    private final String code;
    private final String description;
    private final int priority;
    private final String color;

    RiskLevelEnum(String code, String description, int priority, String color) {
        this.code = code;
        this.description = description;
        this.priority = priority;
        this.color = color;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public String getColor() {
        return color;
    }

    /**
     * 根据code获取枚举
     */
    public static RiskLevelEnum fromCode(String code) {
        for (RiskLevelEnum level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据分数获取风险等级
     */
    public static RiskLevelEnum fromScore(Integer score) {
        if (score == null) {
            return UNKNOWN;
        }
        if (score < 40) {
            return HIGH;
        } else if (score < 70) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
}
