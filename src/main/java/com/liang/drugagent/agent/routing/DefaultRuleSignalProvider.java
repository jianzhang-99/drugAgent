package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.controller.request.agent.DrugAgentReq;
import com.liang.drugagent.agent.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认规则信号提供者。
 *
 * <p>将现有规则逻辑迁移为信号形式，
 * 作为上层 Agent 的辅助输入。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Component
public class DefaultRuleSignalProvider implements RuleSignalProvider {

    private static final double HIGH_CONFIDENCE = 0.9;
    private static final double MEDIUM_CONFIDENCE = 0.7;

    @Override
    public RuleSignals provide(DrugAgentReq req, AgentContext context) {
        // 1. 显式 sceneHint 优先
        SceneEnum sceneHint = SceneEnum.fromHint(req.getSceneHint());
        if (sceneHint != null && sceneHint != SceneEnum.UNKNOWN) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "explicit_hint");
            indicators.put("sceneHint", sceneHint.name());
            return new RuleSignals(sceneHint.name(), 1.0, "显式指定的场景", indicators);
        }

        // 2. 多文件上传 -> 标书查重
        List<String> fileNames = extractFileNames(req);
        if (fileNames.size() >= 2) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "multi_file");
            indicators.put("fileCount", fileNames.size());
            indicators.put("files", fileNames);
            return new RuleSignals(
                    SceneEnum.TENDER_REVIEW.name(),
                    HIGH_CONFIDENCE,
                    String.format("多文件上传（%d份），高置信指向标书查重", fileNames.size()),
                    indicators
            );
        }

        // 3. 单文件标书关键词 -> 标书查重
        if (fileNames.size() == 1 && isTenderFile(fileNames.get(0))) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "single_tender_file");
            indicators.put("filename", fileNames.get(0));
            return new RuleSignals(
                    SceneEnum.TENDER_REVIEW.name(),
                    HIGH_CONFIDENCE,
                    "文件名含标书相关关键词",
                    indicators
            );
        }

        // 4. 单文件 -> 合同预审
        if (fileNames.size() == 1) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "single_file");
            indicators.put("filename", fileNames.get(0));
            return new RuleSignals(
                    SceneEnum.CONTRACT_PRECHECK.name(),
                    MEDIUM_CONFIDENCE,
                    "单文件上传，判定为合同文件",
                    indicators
            );
        }

        // 5. 纯文本关键词判断
        String query = req.getQuery() != null ? req.getQuery().toLowerCase() : "";

        if (containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似", "对比")) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "query_keyword");
            indicators.put("scene", SceneEnum.TENDER_REVIEW.name());
            indicators.put("matchedKeywords", extractMatchedKeywords(query,
                    "标书", "投标", "串标", "围标", "雷同", "查重", "相似", "对比"));
            return new RuleSignals(
                    SceneEnum.TENDER_REVIEW.name(),
                    HIGH_CONFIDENCE,
                    "查询文本含标书相关关键词",
                    indicators
            );
        }

        if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "query_keyword");
            indicators.put("scene", SceneEnum.CONTRACT_PRECHECK.name());
            indicators.put("matchedKeywords", extractMatchedKeywords(query,
                    "合同", "协议", "条款", "法务", "审核", "预审", "审查"));
            return new RuleSignals(
                    SceneEnum.CONTRACT_PRECHECK.name(),
                    HIGH_CONFIDENCE,
                    "查询文本含合同相关关键词",
                    indicators
            );
        }

        if (containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析")) {
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("type", "query_keyword");
            indicators.put("scene", SceneEnum.RISK_ALERT.name());
            indicators.put("matchedKeywords", extractMatchedKeywords(query,
                    "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析"));
            return new RuleSignals(
                    SceneEnum.RISK_ALERT.name(),
                    HIGH_CONFIDENCE,
                    "查询文本含风险预警相关关键词",
                    indicators
            );
        }

        // 无法决策
        return new RuleSignals(null, 0.0, "规则无法决策，依赖 LLM 判断", Map.of("type", "no_signal"));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractFileNames(DrugAgentReq req) {
        Map<String, Object> metadata = req.getMetadata();
        if (metadata == null) {
            return List.of();
        }

        Object rawFiles = metadata.get("uploadedFiles");
        if (!(rawFiles instanceof List<?> files)) {
            return List.of();
        }

        return files.stream()
                .filter(f -> f instanceof Map)
                .map(f -> (Map<?, ?>) f)
                .map(f -> {
                    Object name = f.get("filename");
                    return name == null ? "" : name.toString();
                })
                .toList();
    }

    private boolean isTenderFile(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase();
        return containsAny(lower, "标书", "投标", "招标", "围标", "串标");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractMatchedKeywords(String query, String... words) {
        return java.util.Arrays.stream(words)
                .filter(word -> query.contains(word.toLowerCase()))
                .toList();
    }
}
