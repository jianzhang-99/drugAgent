package com.liang.drugagent.agent.common.entity;

import lombok.Getter;

/**
 * 消息角色枚举。
 *
 * @author liangjiajian
 */
@Getter
public enum MessageRole {

    /** 用户消息 */
    USER("user"),

    /** 大模型消息 */
    LLM("LLM"),

    /** 系统消息 */
    SYSTEM("system");

    private final String value;

    MessageRole(String value) {
        this.value = value;
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
