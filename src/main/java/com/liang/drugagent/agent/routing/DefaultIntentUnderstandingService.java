package com.liang.drugagent.agent.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.routing.RoutingProperties;
import com.liang.drugagent.shared.utils.CompletableFutureUtils;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;
import com.liang.drugagent.agent.SceneEnum;
import com.liang.drugagent.agent.RoutingPromptConstants;
import com.liang.drugagent.shared.llm.LlmFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 默认意图理解服务实现。
 *
 * <p>基于 LLM 做意图理解与场景识别，
 * Phase 2 核心组件。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Slf4j
@Service
public class DefaultIntentUnderstandingService implements IntentUnderstandingService {

    private static final String SYSTEM_PROMPT = RoutingPromptConstants.SCENE_CLASSIFICATION_SYSTEM_PROMPT;

    private final LlmFacadeService llmFacadeService;
    private final RoutingProperties routingProperties;
    private final ObjectMapper objectMapper;

    public DefaultIntentUnderstandingService(LlmFacadeService llmFacadeService,
                                            RoutingProperties routingProperties,
                                            ObjectMapper objectMapper) {
        this.llmFacadeService = llmFacadeService;
        this.routingProperties = routingProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowRouteDecision understand(IntentUnderstandingContext context) {
        String prompt = buildPrompt(context);

        log.debug("[IntentUnderstanding] Calling LLM, query={}, fileCount={}",
                context.getQuery(), context.getFileCount());

        String rawResponse;
        try {
            rawResponse = callWithTimeout(prompt, SYSTEM_PROMPT, routingProperties.getLlmTimeout());
        } catch (TimeoutException e) {
            log.error("[IntentUnderstanding] LLM call timeout ({}ms)", routingProperties.getLlmTimeout());
            throw new IntentUnderstandingException("LLM 调用超时", e);
        } catch (Exception e) {
            log.error("[IntentUnderstanding] LLM call failed", e);
            throw new IntentUnderstandingException("LLM 调用失败: " + e.getMessage(), e);
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[IntentUnderstanding] LLM returned empty response");
            throw new IntentUnderstandingException("LLM 返回为空");
        }

        log.debug("[IntentUnderstanding] Raw LLM output: {}", rawResponse);

        return parseAndValidate(rawResponse);
    }

    private String buildPrompt(IntentUnderstandingContext context) {
        StringBuilder userContent = new StringBuilder();

        // 用户查询
        userContent.append("用户查询：").append(context.getQuery() != null ? context.getQuery() : "（空）").append("\n");

        // 文件信息
        userContent.append("上传文件数量：").append(context.getFileCount()).append("\n");
        if (context.getUploadedFileNames() != null && !context.getUploadedFileNames().isEmpty()) {
            userContent.append("上传文件名列表：\n");
            for (int i = 0; i < context.getUploadedFileNames().size(); i++) {
                userContent.append("  ").append(i + 1).append(". ").append(context.getUploadedFileNames().get(i)).append("\n");
            }
        } else {
            userContent.append("上传文件名列表：无\n");
        }

        // 规则信号（作为辅助参考）
        if (context.getRuleSignals() != null && !context.getRuleSignals().isEmpty()) {
            userContent.append("\n【参考信息】规则信号检测到：\n");
            context.getRuleSignals().forEach((key, value) -> {
                if (value != null) {
                    userContent.append(String.format("  - %s: %s\n", key, value));
                }
            });
        }

        userContent.append("\n请根据以上信息，判断用户意图所属的业务场景。");

        return userContent.toString();
    }

    private WorkflowRouteDecision parseAndValidate(String rawResponse) {
        try {
            // 尝试提取 JSON（去除可能的首尾标记）
            String jsonStr = extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(jsonStr);

            String sceneStr = node.path("scene").asText("UNKNOWN");
            double confidence = node.path("confidence").asDouble(0.5);
            String reason = node.path("reason").asText("LLM 判断");
            boolean requiresClarification = node.path("requiresClarification").asBoolean(false);
            String clarificationQuestion = node.path("clarificationQuestion").asText("");

            // 验证 scene
            SceneEnum scene = parseScene(sceneStr);
            if (scene == null) {
                log.warn("[IntentUnderstanding] Invalid scene from LLM: {}", sceneStr);
                scene = SceneEnum.UNKNOWN;
            }

            // 置信度校验
            confidence = Math.max(0.0, Math.min(1.0, confidence));

            Map<String, Object> raw = new HashMap<>();
            raw.put("llmRawOutput", rawResponse);

            log.info("[IntentUnderstanding] Intent understood: scene={}, confidence={}, reason={}",
                    scene, confidence, reason);

            return WorkflowRouteDecision.builder()
                    .scene(scene)
                    .source("upper_agent")
                    .reason(reason)
                    .confidence(confidence)
                    .requiresClarification(requiresClarification)
                    .clarificationQuestion(clarificationQuestion)
                    .raw(raw)
                    .build();

        } catch (Exception e) {
            log.error("[IntentUnderstanding] Parse LLM output failed: {}", rawResponse, e);
            throw new IntentUnderstandingException("解析 LLM 输出失败: " + e.getMessage(), e);
        }
    }

    private String extractJson(String raw) {
        // 尝试去除 markdown 代码块
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private SceneEnum parseScene(String sceneStr) {
        if (sceneStr == null || sceneStr.isBlank()) {
            return null;
        }
        try {
            return SceneEnum.valueOf(sceneStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String callWithTimeout(String prompt, String systemPrompt, long timeoutMs) throws TimeoutException {
        try {
            return CompletableFutureUtils.executeWithTimeout(
                    () -> llmFacadeService.chat(prompt, systemPrompt, "intent-routing"),
                    timeoutMs
            );
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("LLM call timeout");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
