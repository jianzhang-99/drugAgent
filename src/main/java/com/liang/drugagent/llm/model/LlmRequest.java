package com.liang.drugagent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 统一 LLM 请求对象
 * 封装所有 LLM provider 的请求参数
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {

    /**
     * Provider 类型
     */
    private LlmProviderType provider;

    /**
     * 模型名称（如 qwen-plus, abab6.5s 等）
     */
    private String model;

    /**
     * 聊天消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 温度参数（控制随机性，0.0-2.0）
     */
    @Builder.Default
    private Float temperature = 0.7f;

    /**
     * 最大生成 token 数
     */
    private Integer maxTokens;

    /**
     * top_p 采样参数
     */
    private Float topP;

    /**
     * 是否流式返回
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * 请求超时时间（毫秒）
     */
    @Builder.Default
    private Integer timeout = 30000;

    /**
     * 会话 ID（用于对话记忆）
     */
    private String sessionId;

    /**
     * 扩展参数
     */
    private Map<String, Object> extraParams;

    /**
     * 聊天消息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {

        /**
         * 消息角色：user, assistant, system
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 消息名称（可选）
         */
        private String name;
    }
}
