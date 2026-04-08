package com.liang.drugagent.shared.speech;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音识别请求。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechRecognitionRequest {

    /**
     * 音频格式：pcm、wav、mp3、opus 等
     */
    @Builder.Default
    private String format = "pcm";

    /**
     * 采样率：16000、8000 等
     */
    @Builder.Default
    private Integer sampleRate = 16000;

    /**
     * 语言：zh、en 等
     */
    @Builder.Default
    private String language = "zh";

    /**
     * 模型名称
     */
    @Builder.Default
    private String model = "qwen-audio-turbo";

    /**
     * 音频 URL（与 audioData 二选一）
     */
    private String url;

    /**
     * 音频二进制数据（与 url 二选一，Base64 编码）
     */
    private String audioData;

    /**
     * 是否返回详细分词信息
     */
    @Builder.Default
    private Boolean verbose = false;
}
