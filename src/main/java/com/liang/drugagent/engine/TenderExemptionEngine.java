package com.liang.drugagent.engine;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.exemption.TenderExemptionExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 标书审查免责引擎。
 * 负责对规则引擎命中的初始风险点进行多维度的“误报免责”处理，防止逻辑僵化和过度触发。
 *
 * @author liangjiajian
 */
@Component
public class TenderExemptionEngine {

    /** 权重分值保留阈值，低于此分值的命中项会被过滤。 */
    private static final int MIN_KEEP_WEIGHT = 60;

    private final List<TenderExemptionExecutor> executors;

    public TenderExemptionEngine(List<TenderExemptionExecutor> executors) {
        this.executors = executors;
    }

    /**
     * 应用免责规则。
     * 遍历所有的免责执行器（如：关联方自动放行、通用模板不计分等），根据执行结果调整命中风险的权重。
     *
     * @param hits 规则引擎识别出的原始命中项
     * @param data 完整的审查输入数据（包含上下文和豁免配置）
     * @return 包含有效命中项和被免责项的结果集
     */
    public ExemptionResult apply(List<RuleHit> hits, TenderReviewData data) {
        ExemptionResult result = new ExemptionResult();
        if (hits == null || hits.isEmpty()) {
            return result;
        }

        List<RuleHit> effectiveHits = new ArrayList<>();
        List<ExemptionHit> exemptionHits = new ArrayList<>();
        for (RuleHit hit : hits) {
            RuleHit workingHit = copy(hit);
            workingHit.setOriginalWeight(hit.getWeight());
            workingHit.setAdjustedWeight(hit.getWeight());
            workingHit.setExempted(Boolean.FALSE);

            if (executors != null) {
                for (TenderExemptionExecutor executor : executors) {
                    Optional<ExemptionHit> exemption = executor.apply(workingHit, data);
                    if (exemption.isEmpty()) {
                        continue;
                    }
                    ExemptionHit exemptionHit = exemption.get();
                    exemptionHits.add(exemptionHit);
                    workingHit.setAdjustedWeight(exemptionHit.getAfterWeight());
                    workingHit.setExempted(Boolean.TRUE);
                    workingHit.setExemptionReason(mergeReason(workingHit.getExemptionReason(), exemptionHit.getReason()));
                }
            }

            if (workingHit.getAdjustedWeight() != null && workingHit.getAdjustedWeight() >= MIN_KEEP_WEIGHT) {
                effectiveHits.add(workingHit);
            }
        }

        effectiveHits.sort(Comparator.comparing(RuleHit::getAdjustedWeight, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RuleHit::getRuleCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(RuleHit::getRuleName, Comparator.nullsLast(String::compareTo)));
        result.setEffectiveHits(effectiveHits);
        result.setExemptionHits(exemptionHits);
        return result;
    }

    private String mergeReason(String existing, String next) {
        if (existing == null || existing.isBlank()) {
            return next;
        }
        if (next == null || next.isBlank() || existing.contains(next)) {
            return existing;
        }
        return existing + "；" + next;
    }

    private RuleHit copy(RuleHit source) {
        RuleHit target = new RuleHit();
        target.setHitId(source.getHitId());
        target.setRuleCode(source.getRuleCode());
        target.setRuleName(source.getRuleName());
        target.setScopeId(source.getScopeId());
        target.setRiskType(source.getRiskType());
        target.setPriority(source.getPriority());
        target.setWeight(source.getWeight());
        target.setMatchedValue(source.getMatchedValue());
        target.setTriggerSummary(source.getTriggerSummary());
        target.setDocumentIds(source.getDocumentIds() == null ? new ArrayList<>() : new ArrayList<>(source.getDocumentIds()));
        target.setFieldIds(source.getFieldIds() == null ? new ArrayList<>() : new ArrayList<>(source.getFieldIds()));
        target.setBlockIds(source.getBlockIds() == null ? new ArrayList<>() : new ArrayList<>(source.getBlockIds()));
        target.setEvidences(copyEvidences(source.getEvidences()));
        target.setVersion(source.getVersion());
        return target;
    }

    private List<RuleEvidence> copyEvidences(List<RuleEvidence> evidences) {
        List<RuleEvidence> copied = new ArrayList<>();
        if (evidences == null) {
            return copied;
        }
        for (RuleEvidence evidence : evidences) {
            RuleEvidence item = new RuleEvidence();
            item.setDocumentId(evidence.getDocumentId());
            item.setFieldId(evidence.getFieldId());
            item.setBlockId(evidence.getBlockId());
            item.setMatchedValue(evidence.getMatchedValue());
            item.setChapterPath(evidence.getChapterPath());
            item.setAnchor(evidence.getAnchor());
            copied.add(item);
        }
        return copied;
    }
}
