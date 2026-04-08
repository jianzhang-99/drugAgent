package com.liang.drugagent.shared.speech;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 语音合成响应。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechSynthesisResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 合成的音频数据（Base64 编码）
     */
    private String audioData;

    /**
     * 音频 URL（流式场景可能返回）
     */
    private String audioUrl;

    /**
     * 音频格式
     */
    private String format;

    /**
     * 音频时长（秒）
     */
    private Double duration;

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
     * 创建错误响应。
     */
    public static SpeechSynthesisResponse error(String errorCode, String errorMessage) {
        return SpeechSynthesisResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
