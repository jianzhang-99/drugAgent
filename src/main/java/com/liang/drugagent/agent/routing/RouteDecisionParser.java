package com.liang.drugagent.agent.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 路由决策 JSON 解析器。
 *
 * <p>负责将百炼模型输出的 JSON 字符串解析为 WorkflowRouteDecision 对象。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class RouteDecisionParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析模型输出的 JSON 字符串。
     *
     * @param rawJson 模型输出的原始 JSON 字符串
     * @return 解析后的 WorkflowRouteDecision 对象；如果解析失败返回 null
     */
    public WorkflowRouteDecision parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("[RouteDecisionParser] 原始输出为空，无法解析");
            return null;
        }

        // 提取 JSON 块（处理可能的 markdown 代码块包装）
        String jsonContent = extractJsonContent(rawJson);

        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(jsonContent, Map.class);

            String sceneStr = getStringValue(map, "scene");
            Double confidence = getDoubleValue(map, "confidence");
            String reason = getStringValue(map, "reason");
            Boolean requiresClarification = getBooleanValue(map, "requiresClarification");
            String clarificationQuestion = getStringValue(map, "clarificationQuestion");

            // 处理空字符串的场景值
            if (sceneStr == null || sceneStr.isBlank()) {
                log.warn("[RouteDecisionParser] scene 字段为空或缺失");
                return null;
            }

            SceneEnum scene = SceneEnum.fromHint(sceneStr);

            return WorkflowRouteDecision.builder()
                    .scene(scene != null ? scene : SceneEnum.UNKNOWN)
                    .source("llm")
                    .confidence(confidence != null ? confidence : 0.0)
                    .reason(reason != null ? reason : "")
                    .requiresClarification(requiresClarification != null && requiresClarification)
                    .clarificationQuestion(clarificationQuestion != null ? clarificationQuestion : "")
                    .raw(map)
                    .build();

        } catch (Exception e) {
            log.error("[RouteDecisionParser] JSON 解析失败, rawJson={}", jsonContent, e);
            return null;
        }
    }

    /**
     * 从可能包含 markdown 代码块的字符串中提取 JSON 内容。
     */
    private String extractJsonContent(String raw) {
        String trimmed = raw.trim();
        // 处理 ```json ... ``` 格式
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                int lastBackticks = trimmed.lastIndexOf("```");
                if (lastBackticks > firstNewline) {
                    return trimmed.substring(firstNewline + 1, lastBackticks).trim();
                }
            }
        }
        return trimmed;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBooleanValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
