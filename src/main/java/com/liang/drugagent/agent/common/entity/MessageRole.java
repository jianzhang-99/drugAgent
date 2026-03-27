package com.liang.drugagent.agent.common.entity;

/**
 * 消息角色枚举。
 *
 * @author liangjiajian
 */
public enum MessageRole {

    /** 用户消息 */
    USER("user"),

    /** 助手消息 */
    ASSISTANT("assistant"),

    /** 系统消息 */
    SYSTEM("system");

    private final String value;

    MessageRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值获取枚举。
     */
    public static MessageRole fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MessageRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }
}
