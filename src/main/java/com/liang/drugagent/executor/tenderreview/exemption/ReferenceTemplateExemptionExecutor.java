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
public class ReferenceTemplateExemptionExecutor implements TenderExemptionExecutor {

    private static final List<String> REFERENCE_KEYWORDS = List.of(
            "according to", "pursuant to", "regulation", "standard", "tender file", "procurement file",
            "party a", "party b", "purchaser", "bidder", "template",
            "\u6839\u636e", "\u4f9d\u636e", "\u6761\u4f8b", "\u529e\u6cd5", "\u89c4\u8303", "\u6807\u51c6",
            "\u62db\u6807\u6587\u4ef6", "\u91c7\u8d2d\u6587\u4ef6", "\u6295\u6807\u987b\u77e5",
            "\u7532\u65b9", "\u4e59\u65b9", "\u91c7\u8d2d\u4eba", "\u6295\u6807\u4eba"
    );

    @Override
    public Optional<ExemptionHit> apply(RuleHit hit, TenderReviewData data) {
        if (hit == null) {
            return Optional.empty();
        }

        String text = buildText(hit);
        long keywordCount = REFERENCE_KEYWORDS.stream().filter(text::contains).count();
        if (keywordCount < 2) {
            return Optional.empty();
        }

        int before = currentWeight(hit);
        int after = Math.max(before - 25, 0);
        if (after == before) {
            return Optional.empty();
        }

        ExemptionHit exemptionHit = new ExemptionHit();
        exemptionHit.setHitId(hit.getHitId());
        exemptionHit.setRuleCode(hit.getRuleCode());
        exemptionHit.setRuleName(hit.getRuleName());
        exemptionHit.setExemptionType("REFERENCE_TEMPLATE");
        exemptionHit.setAction("DOWNGRADE");
        exemptionHit.setReason("Hit content looks like regulation citation or tender template boilerplate.");
        exemptionHit.setBeforeWeight(before);
        exemptionHit.setAfterWeight(after);
        return Optional.of(exemptionHit);
    }

    private String buildText(RuleHit hit) {
        StringBuilder builder = new StringBuilder();
        append(builder, hit.getMatchedValue());
        append(builder, hit.getTriggerSummary());
        if (hit.getEvidences() != null) {
            for (RuleEvidence evidence : hit.getEvidences()) {
                append(builder, evidence.getMatchedValue());
                append(builder, evidence.getChapterPath());
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private int currentWeight(RuleHit hit) {
        if (hit.getAdjustedWeight() != null) {
            return hit.getAdjustedWeight();
        }
        return hit.getWeight() == null ? 0 : hit.getWeight();
    }
}
