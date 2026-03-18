package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceAssemblerServiceTest {

    private final EvidenceAssemblerService evidenceAssemblerService = new EvidenceAssemblerService();

    @Test
    void shouldGroupEvidenceBySignalType() {
        RuleHit pricingHit = new RuleHit();
        pricingHit.setRuleCode("W-M1");
        pricingHit.setRuleName("quote_gradient");
        pricingHit.setTriggerSummary("pricing gradient anomaly");
        pricingHit.setAdjustedWeight(90);
        pricingHit.setDocumentIds(List.of("DOC-A", "DOC-B"));
        pricingHit.setEvidences(List.of(evidence("DOC-A", "quote list", "migration")));

        RuleHit teamHit = new RuleHit();
        teamHit.setRuleCode("W-M3");
        teamHit.setRuleName("team_overlap");
        teamHit.setTriggerSummary("core team overlap");
        teamHit.setAdjustedWeight(95);
        teamHit.setDocumentIds(List.of("DOC-A", "DOC-B"));
        teamHit.setEvidences(List.of(evidence("DOC-A", "core team", "zhou")));

        ExemptionHit exemptionHit = new ExemptionHit();
        exemptionHit.setRuleName("proposal_copy");
        exemptionHit.setAction("DOWNGRADE");
        exemptionHit.setReason("template content");
        exemptionHit.setBeforeWeight(85);
        exemptionHit.setAfterWeight(55);

        RiskFusionResult fusionResult = new RiskFusionResult();
        fusionResult.setRiskLevel("HIGH");
        fusionResult.setScore(93);
        fusionResult.setSummary("fusion summary");
        fusionResult.setReasonCodes(List.of("HIGH_PRIORITY_RULE", "MULTI_RULE_CO_OCCURRENCE"));

        EvidenceAssemblyResult result = evidenceAssemblerService.assemble(
                List.of(pricingHit, teamHit),
                List.of(exemptionHit),
                fusionResult
        );

        assertEquals(4, result.getGroups().size());
        assertTrue(result.getGroups().stream().anyMatch(group -> "risk_fusion".equals(group.getGroupKey())));
        assertTrue(result.getGroups().stream().anyMatch(group -> "pricing".equals(group.getGroupKey())));
        assertTrue(result.getGroups().stream().anyMatch(group -> "team".equals(group.getGroupKey())));
        assertTrue(result.getGroups().stream().anyMatch(group -> "exemptions".equals(group.getGroupKey())));
        assertTrue(result.getFlatItems().size() >= 4);
    }

    private RuleEvidence evidence(String documentId, String chapterPath, String matchedValue) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(documentId);
        evidence.setChapterPath(chapterPath);
        evidence.setMatchedValue(matchedValue);
        return evidence;
    }
}
