package com.liang.drugagent.agent.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.prompt.AgentPrompts;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.agent.chat.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;
import com.liang.drugagent.shared.llm.LlmFacadeService;
import com.liang.drugagent.shared.utils.CompletableFutureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Agent 路由服务。
 *
 * <p>负责场景路由判断的完整流程：
 * <ol>
 *   <li>上下文补充</li>
 *   <li>规则信号检测</li>
 *   <li>LLM 意图理解</li>
 *   <li>决策融合</li>
 *   <li>澄清判断</li>
 * </ol>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRouteService {

    private static final String SYSTEM_PROMPT = AgentPrompts.SCENE_CLASSIFICATION;

    private static final double HIGH_CONFIDENCE = 0.9;
    private static final double MEDIUM_CONFIDENCE = 0.7;

    private final LlmFacadeService llmFacadeService;
    private final RouteConfig routeConfig;
    private final ObjectMapper objectMapper;

    // ==================== 公共入口 ====================

    /**
     * 执行完整路由判断。
     */
    public WorkflowRouteDecision route(DrugAgentReq req, AgentChatContext context) {
        // Step 1: 收集上下文
        RouteContext routeContext = enrichContext(req);

        // Step 2: 获取规则信号
        RuleSignals ruleSignals = detectRuleSignal(req);

        // Step 3: LLM 意图理解
        WorkflowRouteDecision llmDecision = understandByLlm(routeContext);

        // Step 4: 融合决策
        WorkflowRouteDecision finalDecision = mergeDecision(llmDecision, ruleSignals);
        context.setSceneType(finalDecision.getScene());

        log.info("[AgentRoute] Final decision: scene={}, source={}, confidence={}",
                finalDecision.getScene(), finalDecision.getSource(), finalDecision.getConfidence());

        return finalDecision;
    }

    /**
     * 判断是否需要澄清。
     */
    public boolean shouldClarify(WorkflowRouteDecision decision) {
        if (decision != null && decision.isRequiresClarification()) {
            return true;
        }
        if (decision == null || decision.getScene() == SceneEnum.UNKNOWN) {
            return true;
        }
        Double confidence = decision.getConfidence();
        if (confidence == null || confidence < routeConfig.getConfidenceLowThreshold()) {
            return true;
        }
        return false;
    }

    /**
     * 生成澄清问题。
     */
    public String generateClarificationQuestion(WorkflowRouteDecision decision) {
        if (decision != null && decision.getClarificationQuestion() != null
                && !decision.getClarificationQuestion().isBlank()) {
            return decision.getClarificationQuestion();
        }

        return switch (decision != null && decision.getScene() != null
                ? decision.getScene().name() : "UNKNOWN") {
            case "TENDER_REVIEW" -> "我理解您想要进行标书相关分析。请问您是想要：\n1）比对两份标书的相似度？\n2）检查标书是否存在围标嫌疑？\n3）其他标书审查需求？";
            case "CONTRACT_PRECHECK" -> "我理解您想要进行合同相关审核。请问您是想要：\n1）审核合同条款的风险？\n2）检查合同条款的合规性？\n3）其他合同相关需求？";
            case "RISK_ALERT" -> "我理解您想要进行风险分析。请问您是想要：\n1）分析药品/耗材的用量趋势？\n2）检测异常数据预警？\n3）生成统计分析报告？";
            default -> "抱歉，我目前无法确定您的具体需求。请告诉我您想要：\n1）比对标书文件\n2）审核合同条款\n3）分析药品/耗材风险数据\n或者直接描述您的具体需求";
        };
    }

    // ==================== 私有方法 ====================

    private RouteContext enrichContext(DrugAgentReq req) {
        List<String> fileNames = extractFileNames(req);

        List<String> availableScenes = Arrays.stream(SceneEnum.values())
                .filter(s -> s != SceneEnum.UNKNOWN)
                .map(SceneEnum::name)
                .collect(Collectors.toList());

        return new RouteContext(
                req.getQuery(),
                fileNames,
                fileNames.size(),
                req.getSceneHint(),
                availableScenes,
                fileNames.size() > 0
        );
    }

    private RuleSignals detectRuleSignal(DrugAgentReq req) {
        // 1. 显式 sceneHint 优先
        SceneEnum sceneHint = SceneEnum.fromHint(req.getSceneHint());
        if (sceneHint != null && sceneHint != SceneEnum.UNKNOWN) {
            return new RuleSignals(sceneHint.name(), 1.0, "显式指定");
        }

        // 2. 多文件上传 -> 标书查重
        List<String> fileNames = extractFileNames(req);
        if (fileNames.size() >= 2) {
            return new RuleSignals(SceneEnum.TENDER_REVIEW.name(), HIGH_CONFIDENCE, "多文件上传");
        }

        // 3. 单文件标书关键词 -> 标书查重
        if (fileNames.size() == 1 && isTenderFile(fileNames.get(0))) {
            return new RuleSignals(SceneEnum.TENDER_REVIEW.name(), HIGH_CONFIDENCE, "文件名含标书关键词");
        }

        // 4. 单文件 -> 合同预审
        if (fileNames.size() == 1) {
            return new RuleSignals(SceneEnum.CONTRACT_PRECHECK.name(), MEDIUM_CONFIDENCE, "单文件");
        }

        // 5. 纯文本关键词判断
        String query = req.getQuery() != null ? req.getQuery().toLowerCase() : "";

        if (containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似", "对比")) {
            return new RuleSignals(SceneEnum.TENDER_REVIEW.name(), HIGH_CONFIDENCE, "文本含标书关键词");
        }
        if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
            return new RuleSignals(SceneEnum.CONTRACT_PRECHECK.name(), HIGH_CONFIDENCE, "文本含合同关键词");
        }
        if (containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析")) {
            return new RuleSignals(SceneEnum.RISK_ALERT.name(), HIGH_CONFIDENCE, "文本含风险关键词");
        }

        // 无法决策
        return new RuleSignals(null, 0.0, "无信号");
    }

    private WorkflowRouteDecision understandByLlm(RouteContext context) {
        String prompt = buildPrompt(context);

        log.debug("[AgentRoute] Calling LLM, query={}, fileCount={}", context.query(), context.fileCount());

        String rawResponse;
        try {
            rawResponse = callWithTimeout(prompt, SYSTEM_PROMPT, routeConfig.getLlmTimeout());
        } catch (TimeoutException e) {
            log.error("[AgentRoute] LLM call timeout");
            throw new RouteException("LLM 调用超时");
        } catch (Exception e) {
            log.error("[AgentRoute] LLM call failed", e);
            throw new RouteException("LLM 调用失败: " + e.getMessage());
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new RouteException("LLM 返回为空");
        }

        return parseAndValidate(rawResponse);
    }

    private WorkflowRouteDecision mergeDecision(WorkflowRouteDecision llmDecision, RuleSignals ruleSignals) {
        // 无规则信号时采纳 LLM
        if (ruleSignals == null || ruleSignals.confidence() <= 0.0 || ruleSignals.hitScenes() == null) {
            return llmDecision != null ? llmDecision : buildUnknownDecision("LLM 和规则都无法决策");
        }

        // 无 LLM 决策时采纳规则
        if (llmDecision == null) {
            return buildDecisionFromRuleSignals(ruleSignals);
        }

        // 场景一致时融合
        String llmScene = llmDecision.getScene().name();
        String ruleScene = ruleSignals.hitScenes();
        if (llmScene.equals(ruleScene)) {
            return WorkflowRouteDecision.builder()
                    .scene(llmDecision.getScene())
                    .source("merged")
                    .reason("LLM与规则一致")
                    .confidence(Math.max(llmDecision.getConfidence(), ruleSignals.confidence()))
                    .requiresClarification(false)
                    .build();
        }

        // 冲突时：高置信优先
        double llmConf = llmDecision.getConfidence() != null ? llmDecision.getConfidence() : 0.5;
        double ruleConf = ruleSignals.confidence();

        if (ruleConf >= routeConfig.getConfidenceThreshold()) {
            return buildDecisionFromRuleSignals(ruleSignals);
        }
        if (llmConf >= routeConfig.getConfidenceThreshold()) {
            return llmDecision;
        }

        // 双方都低时需要澄清
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("conflict")
                .reason("场景冲突且置信度都不高")
                .confidence(Math.min(llmConf, ruleConf))
                .requiresClarification(true)
                .clarificationQuestion("系统检测到您可能想要执行多个操作。请确认您是想要：1）标书查重分析；2）合同预审；还是3）风险预警分析？")
                .build();
    }

    // ==================== 工具方法 ====================

    private List<String> extractFileNames(DrugAgentReq req) {
        Map<String, Object> metadata = req.getMetadata();
        if (metadata == null) return List.of();

        Object rawFiles = metadata.get("uploadedFiles");
        if (!(rawFiles instanceof List<?> files)) return List.of();

        return files.stream()
                .filter(f -> f instanceof Map)
                .map(f -> (Map<?, ?>) f)
                .map(f -> f.get("filename") != null ? f.get("filename").toString() : "")
                .toList();
    }

    private boolean isTenderFile(String filename) {
        if (filename == null || filename.isBlank()) return false;
        return containsAny(filename.toLowerCase(), "标书", "投标", "招标", "围标", "串标");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase())) return true;
        }
        return false;
    }

    private String buildPrompt(RouteContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户查询：").append(context.query() != null ? context.query() : "（空）").append("\n");
        sb.append("上传文件数量：").append(context.fileCount()).append("\n");
        if (!context.uploadedFileNames().isEmpty()) {
            sb.append("上传文件名列表：\n");
            for (int i = 0; i < context.uploadedFileNames().size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(context.uploadedFileNames().get(i)).append("\n");
            }
        }
        sb.append("\n请根据以上信息，判断用户意图所属的业务场景。");
        return sb.toString();
    }

    private WorkflowRouteDecision parseAndValidate(String rawResponse) {
        try {
            String jsonStr = extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(jsonStr);

            String sceneStr = node.path("scene").asText("UNKNOWN");
            double confidence = Math.max(0.0, Math.min(1.0, node.path("confidence").asDouble(0.5)));
            String reason = node.path("reason").asText("LLM 判断");
            boolean requiresClarification = node.path("requiresClarification").asBoolean(false);
            String clarificationQuestion = node.path("clarificationQuestion").asText("");

            SceneEnum scene;
            try {
                scene = SceneEnum.valueOf(sceneStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                scene = SceneEnum.UNKNOWN;
            }

            return WorkflowRouteDecision.builder()
                    .scene(scene)
                    .source("llm")
                    .reason(reason)
                    .confidence(confidence)
                    .requiresClarification(requiresClarification)
                    .clarificationQuestion(clarificationQuestion)
                    .build();

        } catch (Exception e) {
            log.error("[AgentRoute] Parse LLM output failed", e);
            throw new RouteException("解析 LLM 输出失败: " + e.getMessage());
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
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

    private WorkflowRouteDecision buildDecisionFromRuleSignals(RuleSignals ruleSignals) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.valueOf(ruleSignals.hitScenes()))
                .source("rule")
                .reason(ruleSignals.reason())
                .confidence(ruleSignals.confidence())
                .requiresClarification(false)
                .build();
    }

    private WorkflowRouteDecision buildUnknownDecision(String reason) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason(reason)
                .confidence(0.0)
                .requiresClarification(false)
                .build();
    }

    // ==================== 内部类 ====================

    public static class RouteException extends RuntimeException {
        public RouteException(String message) {
            super(message);
        }
    }

    public record RuleSignals(String hitScenes, double confidence, String reason) {}

    public record RouteContext(
            String query,
            List<String> uploadedFileNames,
            int fileCount,
            String sceneHint,
            List<String> availableScenes,
            boolean hasAttachments
    ) {}

    @ConfigurationProperties(prefix = "agent.routing")
    public static class RouteConfig {
        private boolean llmEnabled = true;
        private int llmTimeout = 10000;
        private double confidenceThreshold = 0.75;
        private double confidenceLowThreshold = 0.5;

        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean llmEnabled) { this.llmEnabled = llmEnabled; }
        public int getLlmTimeout() { return llmTimeout; }
        public void setLlmTimeout(int llmTimeout) { this.llmTimeout = llmTimeout; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
        public double getConfidenceLowThreshold() { return confidenceLowThreshold; }
        public void setConfidenceLowThreshold(double confidenceLowThreshold) { this.confidenceLowThreshold = confidenceLowThreshold; }
    }
}
