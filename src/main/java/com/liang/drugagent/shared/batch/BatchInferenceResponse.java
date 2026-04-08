package com.liang.drugagent.shared.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 批量推理响应。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInferenceResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 批量任务 ID
     */
    private String batchId;

    /**
     * 批量任务状态：pending, in_progress, completed, failed
     */
    private String status;

    /**
     * 批量结果列表
     */
    private List<BatchResult> results;

    /**
     * 完成数量
     */
    private Integer completedCount;

    /**
     * 失败数量
     */
    private Integer failedCount;

    /**
     * 总数量
     */
    private Integer totalCount;

    /**
     * 错误码（失败时）
     */
    private String errorCode;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 批量结果项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchResult {
        /**
         * 输入项 ID
         */
        private String id;

        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 生成的文本内容
         */
        private String content;

        /**
         * 完成原因
         */
        private String finishReason;

        /**
         * 错误码（失败时）
         */
        private String errorCode;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * token 使用量统计
         */
        private TokenUsage tokenUsage;
    }

    /**
     * Token 使用量。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
    }

    /**
     * 创建错误响应。
     */
    public static BatchInferenceResponse error(String errorCode, String errorMessage) {
        return BatchInferenceResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
