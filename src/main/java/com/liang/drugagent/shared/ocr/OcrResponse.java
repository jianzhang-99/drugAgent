package com.liang.drugagent.shared.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OCR 文档解析响应。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 提取的完整文本内容
     */
    private String text;

    /**
     * 结构化的文本块列表
     */
    private List<TextBlock> blocks;

    /**
     * 表格列表
     */
    private List<TableResult> tables;

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
    public static OcrResponse success(String text, String model) {
        return OcrResponse.builder()
                .success(true)
                .text(text)
                .model(model)
                .build();
    }

    /**
     * 创建成功响应（带结构和表格）
     */
    public static OcrResponse success(String text, List<TextBlock> blocks, List<TableResult> tables, String model) {
        return OcrResponse.builder()
                .success(true)
                .text(text)
                .blocks(blocks)
                .tables(tables)
                .model(model)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static OcrResponse error(String errorCode, String errorMessage) {
        return OcrResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 文本块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextBlock {
        /**
         * 文本内容
         */
        private String text;
        /**
         * 所在页码
         */
        private Integer page;
        /**
         * 左上角 x 坐标
         */
        private Double x;
        /**
         * 左上角 y 坐标
         */
        private Double y;
        /**
         * 宽度
         */
        private Double width;
        /**
         * 高度
         */
        private Double height;
        /**
         * 置信度
         */
        private Double confidence;
    }

    /**
     * 表格结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableResult {
        /**
         * 表格内容（CSV 格式）
         */
        private String csvContent;
        /**
         * HTML 格式的表格
         */
        private String htmlContent;
        /**
         * 所在页码
         */
        private Integer page;
        /**
         * 起始行
         */
        private Integer startRow;
        /**
         * 结束行
         */
        private Integer endRow;
    }
}
