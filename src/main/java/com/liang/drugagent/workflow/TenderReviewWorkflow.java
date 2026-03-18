package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.engine.TenderExemptionEngine;
import com.liang.drugagent.engine.TenderRuleEngine;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import com.liang.drugagent.service.tenderreview.EvidenceAssemblerService;
import com.liang.drugagent.service.tenderreview.RiskFusionService;
import com.liang.drugagent.service.tenderreview.TenderReviewDataResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TenderReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;
    private final TenderRuleEngine tenderRuleEngine;
    private final TenderExemptionEngine tenderExemptionEngine;
    private final RiskFusionService riskFusionService;
    private final EvidenceAssemblerService evidenceAssemblerService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataResolver tenderReviewDataResolver;

    public TenderReviewWorkflow(AgentChatService agentChatService,
                                TenderRuleEngine tenderRuleEngine,
                                TenderExemptionEngine tenderExemptionEngine,
                                RiskFusionService riskFusionService,
                                EvidenceAssemblerService evidenceAssemblerService,
                                ObjectMapper objectMapper,
                                TenderReviewDataResolver tenderReviewDataResolver) {
        this.agentChatService = agentChatService;
        this.tenderRuleEngine = tenderRuleEngine;
        this.tenderExemptionEngine = tenderExemptionEngine;
        this.riskFusionService = riskFusionService;
        this.evidenceAssemblerService = evidenceAssemblerService;
        this.objectMapper = objectMapper;
        this.tenderReviewDataResolver = tenderReviewDataResolver;
    }

    @Override
    public SceneEnum support() {
        return SceneEnum.TENDER_REVIEW;
    }

    @Override
    public WorkflowResult execute(AgentContext context) {
        TenderReviewData tenderReviewData = readTenderReviewData(context);
        if (tenderReviewData != null) {
            return executeRuleFlow(tenderReviewData);
        }

        String answer = agentChatService.chatWithScene(context.getQuery(), "default", context.getSessionId());
        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, answer);
        result.setRiskLevel("NONE");
        result.setSteps(List.of("scene_route", "generic_review"));
        result.setEvidenceList(List.of(
                new EvidenceItem("system_note", "MVP fallback path is still using generic chat ability.", "system")
        ));
        return result;
    }

    private WorkflowResult executeRuleFlow(TenderReviewData tenderReviewData) {
        RuleResult ruleResult = tenderRuleEngine.execute(tenderReviewData);
        ExemptionResult exemptionResult = tenderExemptionEngine.apply(ruleResult.getHits(), tenderReviewData);
        List<RuleHit> effectiveHits = exemptionResult.getEffectiveHits();
        RiskFusionResult fusionResult = riskFusionService.fuse(
                tenderReviewData,
                effectiveHits,
                exemptionResult.getExemptionHits()
        );
        EvidenceAssemblyResult evidenceAssemblyResult = evidenceAssemblerService.assemble(
                effectiveHits,
                exemptionResult.getExemptionHits(),
                fusionResult
        );

        WorkflowResult result = WorkflowResult.of(
                SceneEnum.TENDER_REVIEW,
                buildAnswer(tenderReviewData, ruleResult.getHits(), effectiveHits, exemptionResult.getExemptionHits(), fusionResult)
        );
        result.setRiskLevel(fusionResult.getRiskLevel());
        result.setSteps(List.of("scene_route", "structured_load", "rule_hit", "false_positive_exemption", "risk_fusion", "evidence_assembly"));
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        return result;
    }

    private TenderReviewData readTenderReviewData(AgentContext context) {
        TenderReviewData resolved = tenderReviewDataResolver.resolve(context);
        if (resolved != null) {
            return resolved;
        }
        Map<String, Object> metadata = context == null ? null : context.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object rawData = metadata.get("tenderReviewData");
        if (rawData == null) {
            return null;
        }
        return objectMapper.convertValue(rawData, TenderReviewData.class);
    }

    private String buildAnswer(TenderReviewData data,
                               List<RuleHit> rawHits,
                               List<RuleHit> effectiveHits,
                               List<ExemptionHit> exemptionHits,
                               RiskFusionResult fusionResult) {
        int documentCount = data.getDocuments() == null ? 0 : data.getDocuments().size();
        int rawHitCount = rawHits == null ? 0 : rawHits.size();
        int effectiveHitCount = effectiveHits == null ? 0 : effectiveHits.size();
        int exemptionCount = exemptionHits == null ? 0 : exemptionHits.size();

        if (effectiveHitCount == 0) {
            if (rawHitCount > 0 && exemptionCount > 0) {
                return "Reviewed " + documentCount + " documents, detected " + rawHitCount
                        + " raw hits, and retained no high-risk hits after exemption. Fusion score="
                        + fusionResult.getScore() + ".";
            }
            return "Reviewed " + documentCount + " documents and found no high-confidence collusion hits. Fusion score="
                    + fusionResult.getScore() + ".";
        }

        String topRules = effectiveHits.stream()
                .limit(3)
                .map(hit -> hit.getRuleName() + " (weight " + effectiveWeight(hit) + ")")
                .collect(Collectors.joining(", "));
        return "Reviewed " + documentCount + " documents, retained " + effectiveHitCount
                + " high-risk rule hits"
                + (exemptionCount > 0 ? ", and downgraded " + exemptionCount + " hits by exemption" : "")
                + ". Fusion score=" + fusionResult.getScore()
                + ", level=" + fusionResult.getRiskLevel()
                + ". Focus: " + topRules + ".";
    }

    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }
}
