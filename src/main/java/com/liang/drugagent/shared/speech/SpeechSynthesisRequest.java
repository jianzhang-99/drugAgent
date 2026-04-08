package com.liang.drugagent.shared.speech;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音合成请求。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechSynthesisRequest {

    /**
     * 要合成语音的文本
     */
    private String text;

    /**
     * 模型名称
     */
    @Builder.Default
    private String model = "cosyvoice-v1";

    /**
     * 语音风格：friendly、professional、customer_service 等
     */
    @Builder.Default
    private String voice = "friendly";

    /**
     * 输出格式：mp3、pcm、wav
     */
    @Builder.Default
    private String format = "mp3";

    /**
     * 采样率
     */
    @Builder.Default
    private Integer sampleRate = 22050;

    /**
     * 语速：0.5 - 2.0
     */
    @Builder.Default
    private Double speed = 1.0;

    /**
     * 音调：0.5 - 2.0
     */
    @Builder.Default
    private Double pitch = 1.0;

    /**
     * 音量：0.0 - 1.0
     */
    @Builder.Default
    private Double volume = 1.0;
}
