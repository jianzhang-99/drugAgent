package com.liang.drugagent.controller;

import com.liang.drugagent.shared.model.Result;
import com.liang.drugagent.shared.speech.DashScopeSpeechService;
import com.liang.drugagent.shared.speech.SpeechRecognitionRequest;
import com.liang.drugagent.shared.speech.SpeechRecognitionResponse;
import com.liang.drugagent.shared.speech.SpeechSynthesisRequest;
import com.liang.drugagent.shared.speech.SpeechSynthesisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音 Controller。
 *
 * <p>提供语音识别和语音合成 API：
 * <ul>
 *   <li>POST /agent/speech/recognize - 语音识别（音频转文本）</li>
 *   <li>POST /agent/speech/synthesize - 语音合成（文本转音频）</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/agent/speech")
@RequiredArgsConstructor
@Tag(name = "Speech", description = "语音识别与语音合成")
public class SpeechController {

    private final DashScopeSpeechService speechService;

    /**
     * 语音识别 - 将音频转为文本。
     *
     * @param file 音频文件
     * @param format 音频格式（pcm, wav, mp3, opus），默认 pcm
     * @param sampleRate 采样率，默认 16000
     * @param language 语言，默认 zh
     * @return 识别结果
     */
    @Operation(summary = "语音识别", description = "将音频文件转换为文本")
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SpeechRecognitionResponse> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "pcm") String format,
            @RequestParam(value = "sampleRate", defaultValue = "16000") Integer sampleRate,
            @RequestParam(value = "language", defaultValue = "zh") String language) {

        log.info("[SpeechController] 语音识别请求 - fileName={}, format={}, sampleRate={}",
                file.getOriginalFilename(), format, sampleRate);

        try {
            SpeechRecognitionRequest request = SpeechRecognitionRequest.builder()
                    .format(format)
                    .sampleRate(sampleRate)
                    .language(language)
                    .audioData(java.util.Base64.getEncoder().encodeToString(file.getBytes()))
                    .build();

            SpeechRecognitionResponse response = speechService.recognize(request);

            if (response.isSuccess()) {
                log.info("[SpeechController] 语音识别成功 - textLength={}",
                        response.getText() != null ? response.getText().length() : 0);
            } else {
                log.warn("[SpeechController] 语音识别失败 - errorCode={}, errorMessage={}",
                        response.getErrorCode(), response.getErrorMessage());
            }

            return Result.success(response);

        } catch (Exception e) {
            log.error("[SpeechController] 语音识别异常: {}", e.getMessage(), e);
            return Result.success(SpeechRecognitionResponse.error("CLIENT_ERROR", "语音识别异常: " + e.getMessage()));
        }
    }

    /**
     * 语音合成 - 将文本转为音频。
     *
     * @param text 要合成的文本
     * @param voice 语音风格，默认 friendly
     * @param format 输出格式，默认 mp3
     * @param speed 语速，默认 1.0
     * @return 合成结果（Base64 编码的音频）
     */
    @Operation(summary = "语音合成", description = "将文本转换为音频")
    @PostMapping(value = "/synthesize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<SpeechSynthesisResponse> synthesize(
            @RequestBody SpeechSynthesisRequest request) {

        log.info("[SpeechController] 语音合成请求 - textLength={}, voice={}",
                request.getText() != null ? request.getText().length() : 0, request.getVoice());

        try {
            SpeechSynthesisResponse response = speechService.synthesize(request);

            if (response.isSuccess()) {
                log.info("[SpeechController] 语音合成成功 - audioDataLength={}",
                        response.getAudioData() != null ? response.getAudioData().length() : 0);
            } else {
                log.warn("[SpeechController] 语音合成失败 - errorCode={}, errorMessage={}",
                        response.getErrorCode(), response.getErrorMessage());
            }

            return Result.success(response);

        } catch (Exception e) {
            log.error("[SpeechController] 语音合成异常: {}", e.getMessage(), e);
            return Result.success(SpeechSynthesisResponse.error("CLIENT_ERROR", "语音合成异常: " + e.getMessage()));
        }
    }
}
