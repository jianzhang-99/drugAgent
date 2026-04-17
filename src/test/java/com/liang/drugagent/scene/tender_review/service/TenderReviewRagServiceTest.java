package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewRagEvidence;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.tool.KnowledgeRetrievalTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderReviewRagServiceTest {

    @Mock
    private KnowledgeRetrievalTool knowledgeRetrievalTool;

    private TenderReviewRagService tenderReviewRagService;

    @BeforeEach
    void setUp() {
        tenderReviewRagService = new TenderReviewRagService(knowledgeRetrievalTool);
    }

    @Test
    void shouldSkipWhenOrgIdMissing() {
        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                null,
                "trace-1"
        );

        assertEquals("SKIPPED", result.getStatus());
        assertEquals("MISSING_ORG_ID", result.getReason());
        verify(knowledgeRetrievalTool, never()).search(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void shouldBuildLegalEvidenceGroupFromRagOutcome() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("HIT")
                        .reason("HIT")
                        .evidenceList(List.of(EvidenceItem.builder()
                                .title("招标投标法")
                                .content("投标人不得相互串通投标。")
                                .source("[chunk-1] law-1")
                                .build()))
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        assertEquals("SUPPORTED", result.getStatus());
        assertEquals(1, result.getHitCount());
        assertNotNull(result.getGroup());
        assertEquals("rag_legal_basis", result.getGroup().getGroupKey());
        assertEquals("法规与审查依据", result.getGroup().getTitle());
        assertTrue(result.getItems().get(0).getTitle().contains("报价异常依据"));
        assertTrue(result.getItems().get(0).getContent().contains("投标人不得相互串通投标"));
    }

    @Test
    void shouldDeduplicateQueriesByRiskType() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(
                        buildHit("W-M2", "联系方式近邻"),
                        buildHit("W-M2", "联系方式近邻"),
                        buildHit("W-M3", "团队成员重叠")
                ),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool, org.mockito.Mockito.times(2))
                .search(queryCaptor.capture(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3));
        assertTrue(queryCaptor.getAllValues().get(0).contains("联系方式"));
        assertTrue(queryCaptor.getAllValues().get(1).contains("团队"));
    }

    private RuleHit buildHit(String ruleCode, String ruleName) {
        return RuleHit.builder()
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .weight(80)
                .priority("HIGH")
                .build();
    }
}
