package com.liang.drugagent.shared.llm;

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
     * 可用工具列表（Function Calling）
     */
    private List<ToolDefinition> tools;

    /**
     * 强制使用的工具（可选，用于限制模型只能调用指定工具）
     */
    private String toolChoice;

    /**
     * 输出格式：null（默认）/ json_object / json_schema
     */
    private String responseFormat;

    /**
     * 是否启用深度思考模式（默认启用）
     * 设置为 false 可降低延迟和成本，适用于简单问答
     * 复杂推理和长报告生成时可启用
     */
    @Builder.Default
    private Boolean thinkingEnabled = true;

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
         * 消息内容（纯文本）
         */
        private String content;

        /**
         * 多模态内容（支持图片、视频等）
         * 每个元素可以是：
         * - {"text": "文本内容"}
         * - {"image_url": {"url": "https://..."}}
         * - {"image_base64": "base64数据", "image_type": "image/jpeg"}
         */
        private List<Map<String, Object>> multiModalContent;

        /**
         * 消息名称（可选）
         */
        private String name;

        /**
         * 工具调用（当 role=assistant 且模型选择调用工具时）
         */
        private ToolCall toolCall;

        /**
         * 判断是否有图片内容。
         */
        public boolean hasImageContent() {
            if (multiModalContent == null || multiModalContent.isEmpty()) {
                return false;
            }
            for (Map<String, Object> item : multiModalContent) {
                if (item.containsKey("image_url") || item.containsKey("image_base64")) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 工具调用内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        /**
         * 调用的工具 ID
         */
        private String id;

        /**
         * 调用的工具名称
         */
        private String name;

        /**
         * 工具参数（JSON 格式的字符串）
         */
        private String arguments;
    }
}
