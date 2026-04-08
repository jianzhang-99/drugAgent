package com.liang.drugagent.shared.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一 LLM 响应对象
 * 封装所有 LLM provider 的响应数据
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 响应内容（流式时为增量内容）
     */
    private String content;

    /**
     * 完整响应内容（流式响应完成后填充）
     */
    private String fullContent;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * Provider 类型
     */
    private LlmProviderType provider;

    /**
     * token 使用量信息
     */
    private TokenUsage tokenUsage;

    /**
     * 停止原因（如 length, stop 等）
     */
    private String finishReason;

    /**
     * 是否为流式响应
     */
    @Builder.Default
    private Boolean streamed = false;

    /**
     * 是否为最后一条流式响应
     */
    @Builder.Default
    private Boolean isLast = false;

    /**
     * 响应时间戳
     */
    @Builder.Default
    private LocalDateTime responseTime = LocalDateTime.now();

    /**
     * 错误码（成功时为 null）
     */
    private String errorCode;

    /**
     * 错误信息（失败时填充）
     */
    private String errorMessage;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 工具调用（当模型选择调用工具时填充）
     */
    private ToolCallResult toolCall;

    /**
     * Token 使用量内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {

        /**
         * 输入 token 数
         */
        private Integer promptTokens;

        /**
         * 输出 token 数
         */
        private Integer completionTokens;

        /**
         * 总 token 数
         */
        private Integer totalTokens;
    }

    /**
     * 工具调用结果（Function Calling）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallResult {

        /**
         * 调用 ID
         */
        private String id;

        /**
         * 调用的工具名称
         */
        private String name;

        /**
         * 工具参数（JSON 字符串）
         */
        private String arguments;

        /**
         * 解析后的参数 Map
         */
        private Map<String, Object> parsedArguments;
    }

    /**
     * 创建成功响应
     */
    public static LlmResponse success(String content, LlmProviderType provider, String model) {
        return LlmResponse.builder()
                .success(true)
                .content(content)
                .provider(provider)
                .model(model)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static LlmResponse error(String errorCode, String errorMessage) {
        return LlmResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建流式响应
     */
    public static LlmResponse streamedChunk(String chunk, boolean isLast) {
        return LlmResponse.builder()
                .success(true)
                .content(chunk)
                .streamed(true)
                .isLast(isLast)
                .build();
    }
}
