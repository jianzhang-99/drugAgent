package com.liang.drugagent.executor.tenderreview.exemption;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class LowRiskChapterExemptionExecutor implements TenderExemptionExecutor {

    private static final List<String> LOW_RISK_CHAPTER_KEYWORDS = List.of(
            "company profile", "basic info", "qualification", "certificate", "standard", "regulation", "business clause",
            "\u516c\u53f8\u4ecb\u7ecd", "\u4f01\u4e1a\u6982\u51b5", "\u6295\u6807\u4eba\u57fa\u672c\u4fe1\u606f",
            "\u57fa\u672c\u4fe1\u606f", "\u8d44\u8d28", "\u8363\u8a89", "\u8bc1\u4e66", "\u6cd5\u89c4", "\u6807\u51c6",
            "\u5546\u52a1\u6761\u6b3e"
    );

    @Override
    public Optional<ExemptionHit> apply(RuleHit hit, TenderReviewData data) {
        if (hit == null || hit.getEvidences() == null || hit.getEvidences().isEmpty()) {
            return Optional.empty();
        }
        if (!isPlagiarismLike(hit)) {
            return Optional.empty();
        }

        boolean allLowRisk = hit.getEvidences().stream()
                .map(RuleEvidence::getChapterPath)
                .allMatch(this::isLowRiskChapter);
        if (!allLowRisk) {
            return Optional.empty();
        }

        int before = currentWeight(hit);
        int after = Math.max(before - 30, 0);
        if (after == before) {
            return Optional.empty();
        }

        ExemptionHit exemptionHit = new ExemptionHit();
        exemptionHit.setHitId(hit.getHitId());
        exemptionHit.setRuleCode(hit.getRuleCode());
        exemptionHit.setRuleName(hit.getRuleName());
        exemptionHit.setExemptionType("LOW_RISK_CHAPTER");
        exemptionHit.setAction("DOWNGRADE");
        exemptionHit.setReason("Evidence stays in low-risk chapters such as company profile or qualification.");
        exemptionHit.setBeforeWeight(before);
        exemptionHit.setAfterWeight(after);
        return Optional.of(exemptionHit);
    }

    private boolean isPlagiarismLike(RuleHit hit) {
        String riskType = normalize(hit.getRiskType());
        return riskType.contains("plagiarism");
    }

    private boolean isLowRiskChapter(String chapterPath) {
        String normalized = normalize(chapterPath);
        if (normalized.isBlank()) {
            return false;
        }
        return LOW_RISK_CHAPTER_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private int currentWeight(RuleHit hit) {
        if (hit.getAdjustedWeight() != null) {
            return hit.getAdjustedWeight();
        }
        return hit.getWeight() == null ? 0 : hit.getWeight();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
