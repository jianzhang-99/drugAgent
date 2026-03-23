package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文补充器。
 *
 * <p>在决策前补充文件、会话、元数据等上下文信息，
 * 供上层 Agent 意图理解使用。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Component
public class DefaultContextEnricher implements ContextEnricher {

    private static final List<String> AVAILABLE_SCENES = Arrays.stream(SceneEnum.values())
            .filter(s -> s != SceneEnum.UNKNOWN)
            .map(SceneEnum::name)
            .collect(Collectors.toList());

    @Override
    public IntentUnderstandingContext enrich(DrugAgentReq req, AgentContext context) {
        List<String> fileNames = extractFileNames(req);
        Map<String, Object> ruleSignals = extractRuleSignals(req, context);

        return IntentUnderstandingContext.builder()
                .query(req.getQuery())
                .uploadedFileNames(fileNames)
                .fileCount(fileNames.size())
                .sceneHint(req.getSceneHint())
                .hasAttachments(fileNames.size() > 0)
                .availableScenes(AVAILABLE_SCENES)
                .ruleSignals(ruleSignals)
                .recentConversationSummary(extractRecentConversationSummary(context))
                .build();
    }

    /**
     * 从请求中提取文件名列表
     */
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
                    return name == null ? "未知文件" : name.toString();
                })
                .collect(Collectors.toList());
    }

    /**
     * 提取规则信号。
     *
     * <p>规则信号作为上层 Agent 的辅助参考，
     * 帮助 LLM 理解用户可能的意图方向。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRuleSignals(DrugAgentReq req, AgentContext context) {
        Map<String, Object> signals = new HashMap<>();

        if (req.getSceneHint() != null && !req.getSceneHint().isBlank()) {
            signals.put("hasSceneHint", true);
            signals.put("sceneHint", req.getSceneHint());
        }

        List<String> fileNames = extractFileNames(req);
        if (!fileNames.isEmpty()) {
            signals.put("hasFiles", true);
            signals.put("fileCount", fileNames.size());

            // 检查文件名是否包含关键词
            boolean hasTenderKeyword = fileNames.stream()
                    .anyMatch(name -> containsAny(name, "标书", "投标", "招标", "围标", "串标"));
            boolean hasContractKeyword = fileNames.stream()
                    .anyMatch(name -> containsAny(name, "合同", "协议", "采购", "协议"));

            signals.put("suspectedTenderFile", hasTenderKeyword);
            signals.put("suspectedContractFile", hasContractKeyword);
        }

        // 文本关键词检测
        String query = req.getQuery() != null ? req.getQuery().toLowerCase() : "";

        signals.put("queryContainsTenderKeyword",
                containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似", "对比"));
        signals.put("queryContainsContractKeyword",
                containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审"));
        signals.put("queryContainsRiskKeyword",
                containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析"));
        signals.put("queryContainsGenericKeyword",
                containsAny(query, "检查", "看看", "有没有", "问题", "分析", "审"));

        // 多文件倾向标书场景
        if (fileNames.size() >= 2) {
            signals.put("multiFileTenderLikely", true);
        }

        return signals;
    }

    /**
     * 提取近期会话摘要。
     *
     * <p>当前实现为简化版本，
     * 后续可接入 ChatSessionService 获取真实历史。</p>
     */
    private String extractRecentConversationSummary(AgentContext context) {
        // Phase 1: 暂不接入真实历史，后续扩展
        return null;
    }

    private boolean containsAny(String text, String... words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String word : words) {
            if (lowerText.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
