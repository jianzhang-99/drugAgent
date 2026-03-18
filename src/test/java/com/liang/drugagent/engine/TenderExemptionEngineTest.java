package com.liang.drugagent.engine;

import com.liang.drugagent.domain.tenderreview.Anchor;
import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.exemption.LowRiskChapterExemptionExecutor;
import com.liang.drugagent.exemption.ReferenceTemplateExemptionExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderExemptionEngineTest {

    @Test
    void shouldDowngradePlagiarismHitInLowRiskChapter() {
        TenderExemptionEngine engine = new TenderExemptionEngine(List.of(
                new LowRiskChapterExemptionExecutor(),
                new ReferenceTemplateExemptionExecutor()
        ));

        RuleHit hit = new RuleHit();
        hit.setHitId("H-1");
        hit.setRuleCode("W-P1");
        hit.setRuleName("proposal_copy");
        hit.setRiskType("plagiarism");
        hit.setPriority("MEDIUM_HIGH");
        hit.setWeight(85);
        hit.setTriggerSummary("Two documents are highly similar in low-risk sections.");
        hit.setMatchedValue("according to procurement standard");

        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId("DOC-A");
        evidence.setFieldId("F-1");
        evidence.setBlockId("B-1");
        evidence.setMatchedValue("according to procurement file and related standard");
        evidence.setChapterPath("company profile and qualification");
        evidence.setAnchor(new Anchor());
        hit.setEvidences(List.of(evidence));

        ExemptionResult result = engine.apply(List.of(hit), new TenderReviewData());

        assertEquals(1, result.getExemptionHits().size());
        assertEquals(1, result.getEffectiveHits().size());
        assertTrue(Boolean.TRUE.equals(result.getEffectiveHits().get(0).getExempted()));
        assertTrue(result.getEffectiveHits().get(0).getAdjustedWeight() < 85);
    }

    @Test
    void shouldFilterHitWhenWeightFallsBelowThreshold() {
        TenderExemptionEngine engine = new TenderExemptionEngine(List.of(
                new LowRiskChapterExemptionExecutor(),
                new ReferenceTemplateExemptionExecutor()
        ));

        RuleHit hit = new RuleHit();
        hit.setHitId("H-2");
        hit.setRuleCode("W-P3");
        hit.setRuleName("service_commitment_copy");
        hit.setRiskType("plagiarism");
        hit.setPriority("MEDIUM_HIGH");
        hit.setWeight(62);
        hit.setTriggerSummary("Two documents are highly similar in template section.");
        hit.setMatchedValue("according to procurement file and related standard");

        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId("DOC-A");
        evidence.setFieldId("F-2");
        evidence.setBlockId("B-2");
        evidence.setMatchedValue("according to procurement file and related standard");
        evidence.setChapterPath("basic info");
        hit.setEvidences(List.of(evidence));

        ExemptionResult result = engine.apply(List.of(hit), new TenderReviewData());

        assertFalse(result.getExemptionHits().isEmpty());
        assertTrue(result.getEffectiveHits().isEmpty());
    }
}
