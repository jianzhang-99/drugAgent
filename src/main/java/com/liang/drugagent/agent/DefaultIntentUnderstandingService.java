package com.liang.drugagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.QwenService;
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

    private static final String SYSTEM_PROMPT = """
            你是一个专业的医药监管场景分类助手。
            根据用户输入的查询内容、上传的文件信息，判断用户意图属于哪个业务场景。

            支持的场景定义如下：
            - TENDER_REVIEW：标书雷同与语义查重场景。当用户请求比对多份标书、识别串标/围标/雷同风险、进行投标文件相似度分析时，属于此场景。
            - CONTRACT_PRECHECK：合同文件 AI 预审核场景。当用户提交合同文件并要求审核条款、识别风险、进行合规性检查时，属于此场景。
            - RISK_ALERT：医疗耗材与药品合规风险预警场景。当用户查询药品/耗材的用量趋势、异常预警、统计分析时，属于此场景。
            - UNKNOWN：无法确定或不属于上述场景的请求。

            输出要求：
            - 必须严格输出 JSON 格式，不要包含任何其他文字说明
            - JSON 字段说明：
              - scene：匹配的场景枚举值（TENDER_REVIEW / CONTRACT_PRECHECK / RISK_ALERT / UNKNOWN）
              - confidence：置信度，范围 0.0 ~ 1.0
              - reason：对分类原因的简要描述（中文，1-2句话）
              - requiresClarification：是否需要向用户澄清意图（true/false）
              - clarificationQuestion：如果需要澄清，填写追问问题；否则填空字符串

            注意：
            - 如果用户上传了多个文件（>=2个），优先考虑 TENDER_REVIEW 场景
            - 如果用户上传了单个文件，根据文件名和查询内容综合判断
            - 置信度要综合考虑查询文本、文件数量、文件名的匹配程度
            """;

    private final QwenService qwenService;
    private final RoutingProperties routingProperties;
    private final ObjectMapper objectMapper;

    public DefaultIntentUnderstandingService(QwenService qwenService,
                                            RoutingProperties routingProperties,
                                            ObjectMapper objectMapper) {
        this.qwenService = qwenService;
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
        ThreadCompletableFuture<String> future = new ThreadCompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                future.complete(qwenService.chat(prompt, systemPrompt));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        thread.start();

        try {
            return future.get(timeoutMs);
        } catch (java.util.concurrent.TimeoutException e) {
            thread.interrupt();
            throw new TimeoutException("LLM call timeout");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class ThreadCompletableFuture<T> {
        private T result;
        private Exception exception;
        private boolean done = false;

        public synchronized T get(long timeoutMs) throws Exception {
            long start = System.currentTimeMillis();
            while (!done) {
                long remaining = timeoutMs - (System.currentTimeMillis() - start);
                if (remaining <= 0) {
                    throw new java.util.concurrent.TimeoutException();
                }
                wait(remaining);
            }
            if (exception != null) {
                throw exception;
            }
            return result;
        }

        public synchronized void complete(T result) {
            this.result = result;
            this.done = true;
            notifyAll();
        }

        public synchronized void completeExceptionally(Exception e) {
            this.exception = e;
            this.done = true;
            notifyAll();
        }
    }
}
