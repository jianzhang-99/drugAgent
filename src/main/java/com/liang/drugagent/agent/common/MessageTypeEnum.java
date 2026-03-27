package com.liang.drugagent.agent.common;

/**
 * AI 助手消息类型枚举。
 *
 * <p>用于区分 AI 助手的不同回复类型：
 * <ul>
 *   <li>{@code assistant_text} - 普通文本回复</li>
 *   <li>{@code assistant_clarify} - 需要用户澄清的问题</li>
 *   <li>{@code assistant_result_card} - 结构化结果卡片（如审查报告）</li>
 * </ul>
 *
 * @author liangjiajian
 */
public enum MessageTypeEnum {

    /**
     * 普通文本回复。
     */
    ASSISTANT_TEXT("assistant_text"),

    /**
     * 需要用户澄清的问题。
     */
    ASSISTANT_CLARIFY("assistant_clarify"),

    /**
     * 结构化结果卡片。
     */
    ASSISTANT_RESULT_CARD("assistant_result_card");

    private final String code;

    MessageTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MessageTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MessageTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
