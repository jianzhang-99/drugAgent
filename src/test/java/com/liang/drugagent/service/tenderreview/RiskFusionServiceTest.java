package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskFusionServiceTest {

    private final RiskFusionService riskFusionService = new RiskFusionService();

    @Test
    void shouldProduceHighRiskWhenMultipleStrongHitsExist() {
        RuleHit hit1 = buildHit("W-M2", "HIGH", 98, List.of("DOC-A", "DOC-B"), 4);
        RuleHit hit2 = buildHit("W-M3", "HIGH", 95, List.of("DOC-A", "DOC-B"), 2);

        RiskFusionResult result = riskFusionService.fuse(new TenderReviewData(), List.of(hit1, hit2), List.of());

        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.getScore() >= 85);
        assertTrue(result.getReasonCodes().contains("HIGH_PRIORITY_RULE"));
        assertTrue(result.getReasonCodes().contains("MULTI_RULE_CO_OCCURRENCE"));
    }

    @Test
    void shouldDowngradeScoreWhenOnlyExemptedSignalsRemain() {
        ExemptionHit exemptionHit = new ExemptionHit();
        exemptionHit.setHitId("H-1");
        exemptionHit.setRuleCode("W-P1");
        exemptionHit.setAfterWeight(40);

        RiskFusionResult result = riskFusionService.fuse(new TenderReviewData(), List.of(), List.of(exemptionHit));

        assertEquals("LOW", result.getRiskLevel());
        assertEquals(20, result.getScore());
        assertTrue(result.getReasonCodes().contains("EXEMPTION_DOWNGRADE"));
    }

    private RuleHit buildHit(String ruleCode, String priority, int weight, List<String> documentIds, int evidenceCount) {
        RuleHit hit = new RuleHit();
        hit.setRuleCode(ruleCode);
        hit.setRuleName(ruleCode);
        hit.setPriority(priority);
        hit.setWeight(weight);
        hit.setAdjustedWeight(weight);
        hit.setDocumentIds(documentIds);
        hit.setEvidences(buildEvidence(evidenceCount));
        return hit;
    }

    private List<RuleEvidence> buildEvidence(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    RuleEvidence evidence = new RuleEvidence();
                    evidence.setDocumentId("DOC-" + i);
                    evidence.setFieldId("FIELD-" + i);
                    return evidence;
                })
                .toList();
    }
}
