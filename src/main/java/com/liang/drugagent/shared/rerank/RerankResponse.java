package com.liang.drugagent.shared.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank 响应对象。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 排序结果列表
     */
    private List<RerankResult> results;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 创建成功响应
     */
    public static RerankResponse success(List<RerankResult> results, String model) {
        return RerankResponse.builder()
                .success(true)
                .results(results)
                .model(model)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static RerankResponse error(String errorCode, String errorMessage) {
        return RerankResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 单个排序结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankResult {
        /**
         * 文档内容
         */
        private String content;
        /**
         * 在原始列表中的索引
         */
        private Integer index;
        /**
         * 相关性得分
         */
        private Double relevanceScore;
    }
}
