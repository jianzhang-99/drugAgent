package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.engine.TenderRuleEngine;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标书审查工作流实现类。
 * 用于处理标书雷同与语义查重场景。
 *
 * @author liangjiajian
 */
@Component
public class TenderReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;
    private final TenderRuleEngine tenderRuleEngine;
    private final ObjectMapper objectMapper;

    public TenderReviewWorkflow(AgentChatService agentChatService,
                                TenderRuleEngine tenderRuleEngine,
                                ObjectMapper objectMapper) {
        this.agentChatService = agentChatService;
        this.tenderRuleEngine = tenderRuleEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 标书审查场景类型
     */
    @Override
    public SceneEnum support() {
        return SceneEnum.TENDER_REVIEW;
    }

    /**
     * 执行标书审查工作流逻辑。
     * 当前先复用基础对话能力，后续接入标书比对、语义查重和证据汇总能力。
     *
     * @param context Agent 上下文，包含用户问题和会话 ID
     * @return 包含审查内容、执行步骤和证据的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        TenderReviewData tenderReviewData = readTenderReviewData(context.getMetadata());
        if (tenderReviewData != null) {
            return executeRuleFlow(tenderReviewData);
        }

        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "default",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, answer);
        result.setRiskLevel("NONE");
        result.setSteps(List.of("场景识别", "标书查重分析"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本先复用基础问答能力，后续接入标书比对与语义查重能力。", "system")
        ));
        return result;
    }

    private WorkflowResult executeRuleFlow(TenderReviewData tenderReviewData) {
        RuleResult ruleResult = tenderRuleEngine.execute(tenderReviewData);
        List<RuleHit> hits = ruleResult.getHits();

        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, buildAnswer(tenderReviewData, hits));
        result.setRiskLevel(resolveRiskLevel(hits));
        result.setSteps(List.of("场景识别", "结构化载入", "围标规则命中"));
        result.setEvidenceList(buildEvidenceList(hits));
        return result;
    }

    private TenderReviewData readTenderReviewData(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object rawData = metadata.get("tenderReviewData");
        if (rawData == null) {
            return null;
        }
        return objectMapper.convertValue(rawData, TenderReviewData.class);
    }

    private String buildAnswer(TenderReviewData data, List<RuleHit> hits) {
        int documentCount = data.getDocuments() == null ? 0 : data.getDocuments().size();
        if (hits.isEmpty()) {
            return "已完成围标规则扫描，本次比对共检查 " + documentCount + " 份文档，暂未命中高确定性围标规则。";
        }
        String topRules = hits.stream()
                .limit(3)
                .map(hit -> hit.getRuleName() + "（" + hit.getMatchedValue() + "）")
                .collect(Collectors.joining("、"));
        return "已完成围标规则扫描，本次比对共检查 " + documentCount + " 份文档，命中 "
                + hits.size() + " 条规则，重点关注：" + topRules + "。";
    }

    private String resolveRiskLevel(List<RuleHit> hits) {
        if (hits.stream().anyMatch(hit -> "HIGH".equals(hit.getPriority()) && valueAtLeast(hit.getWeight(), 85))) {
            return "HIGH";
        }
        if (!hits.isEmpty()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<EvidenceItem> buildEvidenceList(List<RuleHit> hits) {
        if (hits.isEmpty()) {
            return List.of(new EvidenceItem("规则扫描结果", "未命中联系人、地址或团队复用等高确定性规则。", "rule-engine"));
        }

        List<EvidenceItem> evidenceItems = new ArrayList<>();
        for (RuleHit hit : hits.stream().limit(5).toList()) {
            evidenceItems.add(new EvidenceItem(
                    hit.getRuleName(),
                    hit.getTriggerSummary(),
                    "rule-engine"
            ));
        }
        return evidenceItems;
    }

    private boolean valueAtLeast(Integer value, int threshold) {
        return value != null && value >= threshold;
    }
}
