package com.liang.drugagent.engine;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.exemption.TenderExemptionExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class TenderExemptionEngine {

    private static final int MIN_KEEP_WEIGHT = 60;

    private final List<TenderExemptionExecutor> executors;

    public TenderExemptionEngine(List<TenderExemptionExecutor> executors) {
        this.executors = executors;
    }

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
