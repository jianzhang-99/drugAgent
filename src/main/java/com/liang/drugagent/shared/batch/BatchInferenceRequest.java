package com.liang.drugagent.shared.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 批量推理请求。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInferenceRequest {

    /**
     * 模型名称
     */
    @Builder.Default
    private String model = "qwen-plus";

    /**
     * 批量任务名称（可选）
     */
    private String name;

    /**
     * 批量输入项列表
     */
    private List<BatchItem> items;

    /**
     * 额外参数
     */
    private Map<String, Object> parameters;

    /**
     * 批量输入项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItem {
        /**
         * 唯一标识
         */
        private String id;

        /**
         * 用户提示词
         */
        private String prompt;

        /**
         * 系统提示词（可选）
         */
        private String systemPrompt;

        /**
         * 温度参数（可选）
         */
        private Float temperature;

        /**
         * 最大 token 数（可选）
         */
        private Integer maxTokens;
    }
}
