package com.liang.drugagent.shared.ocr;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 阿里云百炼 OCR 文档解析服务。
 *
 * <p>使用 DashScope SDK 的 Qwen-VL 模型进行文档 OCR 识别，
 * 支持图片、PDF 等文档的文本提取、表格识别、段落结构化。</p>
 *
 * <p>支持的功能：
 * <ul>
 *   <li>通用文字识别（印刷体、手写体）</li>
 *   <li>表格识别（输出 CSV/HTML 格式）</li>
 *   <li>段落结构化</li>
 *   <li>多语言支持</li>
 *   <li>多页文档处理</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeOcrService {

    private final String apiKey;
    private final String defaultModel;

    public DashScopeOcrService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.ocr-model:qwen-vl-ocr}") String defaultModel) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
    }

    /**
     * 执行 OCR 识别。
     *
     * @param request OCR 请求
     * @return OCR 响应
     */
    public OcrResponse recognize(OcrRequest request) {
        long startTime = System.currentTimeMillis();
        String model = request.getFileType() != null ? defaultModel : defaultModel;

        log.info("[OCR] 开始文档解析 - 模型: {}, 文件类型: {}",
                model, request.getFileType() != null ? request.getFileType() : "image");

        try {
            MultiModalConversation conversation = new MultiModalConversation();
            MultiModalConversationResult result = conversation.call(buildParam(request, model));

            OcrResponse response = parseOcrResult(result, model);
            response.setCostMs(System.currentTimeMillis() - startTime);

            log.info("[OCR] 文档解析完成 - 耗时: {}ms, 文本长度: {}",
                    response.getCostMs(),
                    response.getText() != null ? response.getText().length() : 0);

            return response;

        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            log.error("[OCR] 文档解析异常 - 错误: {}", e.getMessage(), e);
            return OcrResponse.error("OCR_ERROR", "OCR 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[OCR] 文档解析异常 - 错误: {}", e.getMessage(), e);
            return OcrResponse.error("OCR_ERROR", "OCR 处理失败: " + e.getMessage());
        }
    }

    /**
     * 执行 OCR 识别（简化版，只返回文本）。
     *
     * @param imageBase64 图片 Base64 编码
     * @return 识别的文本内容
     */
    public String recognizeText(String imageBase64) {
        OcrRequest request = OcrRequest.builder()
                .imageBase64(imageBase64)
                .returnTable(false)
                .returnParagraph(false)
                .build();

        OcrResponse response = recognize(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return response.getText();
        }
        throw new RuntimeException(response.getErrorMessage());
    }

    /**
     * 批量识别多张图片。
     *
     * @param images 图片 Base64 列表
     * @return 识别结果列表
     */
    public List<OcrResponse> recognizeBatch(List<String> images) {
        List<OcrResponse> results = new ArrayList<>();
        for (String image : images) {
            results.add(recognizeTextAsResponse(image));
        }
        return results;
    }

    private OcrResponse recognizeTextAsResponse(String imageBase64) {
        OcrRequest request = OcrRequest.builder()
                .imageBase64(imageBase64)
                .returnTable(false)
                .returnParagraph(false)
                .build();

        return recognize(request);
    }

    @SuppressWarnings("unchecked")
    private MultiModalConversationParam buildParam(OcrRequest request, String model) throws Exception {
        List<MultiModalMessage> messages = new ArrayList<>();

        // 构建提示词
        String prompt = buildOcrPrompt(request);
        messages.add(MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(buildImageContent(request))
                .build());

        // 使用反射构建参数，避免直接依赖特定 API
        Class<?> paramClass = Class.forName("com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam");
        Object builder = paramClass.getMethod("builder").invoke(null);

        builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
        builder.getClass().getMethod("model", String.class).invoke(builder, model);
        builder.getClass().getMethod("messages", List.class).invoke(builder, messages);

        // 设置额外参数
        if (request.getExtraParams() != null && !request.getExtraParams().isBlank()) {
            Map<String, Object> extra = parseExtraParams(request.getExtraParams());
            builder.getClass().getMethod("parameters", Map.class).invoke(builder, extra);
        }

        return (MultiModalConversationParam) builder.getClass().getMethod("build").invoke(builder);
    }

    private String buildOcrPrompt(OcrRequest request) {
        StringBuilder prompt = new StringBuilder();

        if (Boolean.TRUE.equals(request.getReturnTable()) && Boolean.TRUE.equals(request.getReturnParagraph())) {
            prompt.append("请识别文档中的文字内容、表格和段落结构。");
        } else if (Boolean.TRUE.equals(request.getReturnTable())) {
            prompt.append("请识别文档中的文字和表格内容。");
        } else if (Boolean.TRUE.equals(request.getReturnParagraph())) {
            prompt.append("请识别文档中的文字和段落结构。");
        } else {
            prompt.append("请识别文档中的文字内容。");
        }

        if (!"auto".equals(request.getLanguage())) {
            prompt.append(" 语言：").append(request.getLanguage());
        }

        return prompt.toString();
    }

    private List<Map<String, Object>> buildImageContent(OcrRequest request) {
        List<Map<String, Object>> content = new ArrayList<>();

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            // 多页文档
            for (String image : request.getImages()) {
                content.add(buildImageItem(image, "image/jpeg"));
            }
        } else if (request.getImageBase64() != null && !request.getImageBase64().isBlank()) {
            String mimeType = inferMimeType(request.getFileType());
            content.add(buildImageItem(request.getImageBase64(), mimeType));
        } else if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            content.add(Map.of(
                    "image", request.getImageUrl()
            ));
        }

        if (content.isEmpty()) {
            throw new IllegalArgumentException("必须提供 imageBase64、imageUrl 或 images 之一");
        }

        return content;
    }

    private Map<String, Object> buildImageItem(String imageData, String mimeType) {
        Map<String, Object> item = new HashMap<>();
        if (imageData.startsWith("http")) {
            item.put("image", imageData);
        } else {
            item.put("image", "data:" + mimeType + ";base64," + imageData);
        }
        return item;
    }

    private String inferMimeType(String fileType) {
        if (fileType == null) return "image/jpeg";
        return switch (fileType.toLowerCase()) {
            case "png" -> "image/png";
            case "bmp" -> "image/bmp";
            case "tiff", "tif" -> "image/tiff";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
    }

    private Map<String, Object> parseExtraParams(String extraParams) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(extraParams, Map.class);
        } catch (Exception e) {
            log.warn("[OCR] 解析额外参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private OcrResponse parseOcrResult(MultiModalConversationResult result, String model) {
        if (result == null || result.getOutput() == null) {
            return OcrResponse.error("NULL_RESULT", "OCR 返回结果为空");
        }

        MultiModalConversationOutput output = result.getOutput();
        if (output.getChoices() == null || output.getChoices().isEmpty()) {
            return OcrResponse.error("NO_CHOICES", "OCR 返回 choices 为空");
        }

        MultiModalMessage message = output.getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
            return OcrResponse.error("NO_CONTENT", "OCR 返回内容为空");
        }

        // 解析内容
        StringBuilder fullText = new StringBuilder();
        List<OcrResponse.TextBlock> blocks = new ArrayList<>();
        List<OcrResponse.TableResult> tables = new ArrayList<>();

        for (Map<String, Object> item : message.getContent()) {
            if (item.containsKey("text")) {
                String text = (String) item.get("text");
                fullText.append(text).append("\n");

                // 尝试解析结构化信息
                if (item.containsKey("bounding-box")) {
                    Map<String, Object> bbox = (Map<String, Object>) item.get("bounding-box");
                    blocks.add(OcrResponse.TextBlock.builder()
                            .text(text)
                            .x(getDouble(bbox, "x"))
                            .y(getDouble(bbox, "y"))
                            .width(getDouble(bbox, "width"))
                            .height(getDouble(bbox, "height"))
                            .confidence(getDouble(bbox, "confidence"))
                            .build());
                } else if (!text.isBlank()) {
                    blocks.add(OcrResponse.TextBlock.builder()
                            .text(text)
                            .build());
                }
            } else if (item.containsKey("table")) {
                // 表格内容
                Map<String, Object> table = (Map<String, Object>) item.get("table");
                tables.add(OcrResponse.TableResult.builder()
                        .csvContent((String) table.get("csv"))
                        .htmlContent((String) table.get("html"))
                        .build());
            }
        }

        String text = fullText.toString().trim();

        if (!blocks.isEmpty() || !tables.isEmpty()) {
            return OcrResponse.success(text, blocks, tables, model);
        } else {
            return OcrResponse.success(text, model);
        }
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
