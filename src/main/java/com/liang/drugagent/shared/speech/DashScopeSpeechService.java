package com.liang.drugagent.shared.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百炼语音服务（语音识别 + 语音合成）。
 *
 * <p>支持：
 * <ul>
 *   <li>语音识别：将音频转为文本</li>
 *   <li>语音合成：将文本转为音频</li>
 * </ul>
 * </p>
 *
 * <p>使用场景：
 * <ul>
 *   <li>语音问答入口</li>
 *   <li>会议纪要抽取</li>
 *   <li>审查结果播报</li>
 *   <li>访谈录音转写</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeSpeechService {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeSpeechService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.base-url:https://dashscope.aliyuncs.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 语音识别（音频转文本）。
     *
     * @param request 语音识别请求
     * @return 识别结果
     */
    public SpeechRecognitionResponse recognize(SpeechRecognitionRequest request) {
        log.info("[Speech] 开始语音识别 - format={}, sampleRate={}, language={}",
                request.getFormat(), request.getSampleRate(), request.getLanguage());

        try {
            String requestBody = buildRecognitionRequestBody(request);
            String responseBody = callRecognitionApi(requestBody);

            return parseRecognitionResponse(responseBody);

        } catch (Exception e) {
            log.error("[Speech] 语音识别失败: {}", e.getMessage(), e);
            return SpeechRecognitionResponse.error("SPEECH_RECOGNITION_ERROR", "语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 语音合成（文本转音频）。
     *
     * @param request 语音合成请求
     * @return 合成结果
     */
    public SpeechSynthesisResponse synthesize(SpeechSynthesisRequest request) {
        log.info("[Speech] 开始语音合成 - textLength={}, model={}, voice={}",
                request.getText() != null ? request.getText().length() : 0, request.getModel(), request.getVoice());

        try {
            String requestBody = buildSynthesisRequestBody(request);
            String responseBody = callSynthesisApi(requestBody);

            return parseSynthesisResponse(responseBody);

        } catch (Exception e) {
            log.error("[Speech] 语音合成失败: {}", e.getMessage(), e);
            return SpeechSynthesisResponse.error("SPEECH_SYNTHESIS_ERROR", "语音合成失败: " + e.getMessage());
        }
    }

    // ==================== Recognition Methods ====================

    private String buildRecognitionRequestBody(SpeechRecognitionRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());

        Map<String, Object> input = new HashMap<>();
        input.put("format", request.getFormat());
        input.put("sample_rate", request.getSampleRate());
        input.put("language", request.getLanguage());

        if (request.getUrl() != null) {
            input.put("url", request.getUrl());
        } else if (request.getAudioData() != null) {
            input.put("audio_data", request.getAudioData());
        }

        if (request.getVerbose() != null) {
            input.put("verbose", request.getVerbose());
        }

        body.put("input", input);

        return objectMapper.writeValueAsString(body);
    }

    private String callRecognitionApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/audio/asr";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("语音识别 API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private SpeechRecognitionResponse parseRecognitionResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return SpeechRecognitionResponse.error(code, message);
        }

        String text = "";
        String language = null;
        List<SpeechRecognitionResponse.Segment> segments = null;

        JsonNode output = root.get("output");
        if (output != null) {
            if (output.has("text")) {
                text = output.get("text").asText();
            }

            if (output.has("language")) {
                language = output.get("language").asText();
            }

            if (output.has("segments") && output.get("segments").isArray()) {
                segments = new ArrayList<>();
                for (JsonNode seg : output.get("segments")) {
                    SpeechRecognitionResponse.Segment segment = SpeechRecognitionResponse.Segment.builder()
                            .start(seg.has("start") ? seg.get("start").asDouble() : 0)
                            .end(seg.has("end") ? seg.get("end").asDouble() : 0)
                            .text(seg.has("text") ? seg.get("text").asText() : "")
                            .confidence(seg.has("confidence") ? seg.get("confidence").asDouble() : null)
                            .build();
                    segments.add(segment);
                }
            }
        }

        return SpeechRecognitionResponse.builder()
                .success(true)
                .text(text)
                .language(language)
                .segments(segments)
                .build();
    }

    // ==================== Synthesis Methods ====================

    private String buildSynthesisRequestBody(SpeechSynthesisRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());

        Map<String, Object> input = new HashMap<>();
        input.put("text", request.getText());

        Map<String, Object> voiceConfig = new HashMap<>();
        voiceConfig.put("voice", request.getVoice());
        voiceConfig.put("format", request.getFormat());
        voiceConfig.put("sample_rate", request.getSampleRate());
        voiceConfig.put("speed", request.getSpeed());
        voiceConfig.put("pitch", request.getPitch());
        voiceConfig.put("volume", request.getVolume());

        body.put("input", input);
        body.put("voice_config", voiceConfig);

        return objectMapper.writeValueAsString(body);
    }

    private String callSynthesisApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/audio/synthesis";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("语音合成 API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private SpeechSynthesisResponse parseSynthesisResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return SpeechSynthesisResponse.error(code, message);
        }

        String audioData = null;
        String audioUrl = null;
        String format = null;
        Double duration = null;

        JsonNode output = root.get("output");
        if (output != null) {
            if (output.has("audio_data")) {
                audioData = output.get("audio_data").asText();
            }
            if (output.has("audio_url")) {
                audioUrl = output.get("audio_url").asText();
            }
            if (output.has("format")) {
                format = output.get("format").asText();
            }
            if (output.has("duration")) {
                duration = output.get("duration").asDouble();
            }
        }

        return SpeechSynthesisResponse.builder()
                .success(true)
                .audioData(audioData)
                .audioUrl(audioUrl)
                .format(format)
                .duration(duration)
                .build();
    }
}
