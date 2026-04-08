package com.liang.drugagent.shared.speech;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 语音识别响应。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechRecognitionResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 识别出的文本内容
     */
    private String text;

    /**
     * 分段识别结果（verbose 模式）
     */
    private List<Segment> segments;

    /**
     * 语种检测结果
     */
    private String language;

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
     * 识别片段。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        /**
         * 开始时间（秒）
         */
        private double start;

        /**
         * 结束时间（秒）
         */
        private double end;

        /**
         * 该片段的文本
         */
        private String text;

        /**
         * 该片段的置信度
         */
        private Double confidence;
    }

    /**
     * 创建错误响应。
     */
    public static SpeechRecognitionResponse error(String errorCode, String errorMessage) {
        return SpeechRecognitionResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
