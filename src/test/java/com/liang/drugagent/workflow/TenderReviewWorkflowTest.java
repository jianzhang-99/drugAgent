package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.engine.TenderExemptionEngine;
import com.liang.drugagent.engine.TenderRuleEngine;
import com.liang.drugagent.exemption.LowRiskChapterExemptionExecutor;
import com.liang.drugagent.exemption.ReferenceTemplateExemptionExecutor;
import com.liang.drugagent.executor.tenderreview.ContactNearbyExecutor;
import com.liang.drugagent.executor.tenderreview.CoreTeamOverlapExecutor;
import com.liang.drugagent.executor.tenderreview.QuoteGradientExecutor;
import com.liang.drugagent.service.AgentChatService;
import com.liang.drugagent.service.tenderreview.EvidenceAssemblerService;
import com.liang.drugagent.service.tenderreview.RiskFusionService;
import com.liang.drugagent.service.tenderreview.TenderReviewDataResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TenderReviewWorkflowTest {

    @Test
    void shouldRunRuleFlowWhenMetadataContainsRawDocuments() {
        TenderReviewWorkflow workflow = new TenderReviewWorkflow(
                mock(AgentChatService.class),
                new TenderRuleEngine(List.of(
                        new QuoteGradientExecutor(),
                        new ContactNearbyExecutor(),
                        new CoreTeamOverlapExecutor()
                )),
                new TenderExemptionEngine(List.of(
                        new LowRiskChapterExemptionExecutor(),
                        new ReferenceTemplateExemptionExecutor()
                )),
                new RiskFusionService(),
                new EvidenceAssemblerService(),
                new ObjectMapper(),
                new TenderReviewDataResolver(new ObjectMapper())
        );

        DrugAgentReq req = new DrugAgentReq();
        req.setQuery("review these tender documents");
        req.setMetadata(Map.of(
                "documents", List.of(
                        Map.of(
                                "documentId", "DOC-A",
                                "documentName", "bidder-a",
                                "fileType", "md",
                                "content", """
                                        # tender file

                                        ## basic info
                                        | contact person | zhang |
                                        | :--- | :--- |
                                        | phone | 139-1111-2222 |

                                        ## core team
                                        | name | role | years | resume |
                                        | :--- | :--- | :--- | :--- |
                                        | zhou | pm | 11 | led platform delivery |

                                        ## quote list
                                        | no | item | qty | unit price | total |
                                        | :--- | :--- | :--- | :--- | :--- |
                                        | 1 | migration service | 1 | 100000 | 100000 |
                                        | 2 | storage service | 1 | 200000 | 200000 |
                                        | 3 | ops service | 1 | 300000 | 300000 |
                                        """
                        ),
                        Map.of(
                                "documentId", "DOC-B",
                                "documentName", "bidder-b",
                                "fileType", "md",
                                "content", """
                                        # tender file

                                        ## basic info
                                        | contact person | zhang |
                                        | :--- | :--- |
                                        | phone | 139-1111-2229 |

                                        ## core team
                                        | name | role | years | resume |
                                        | :--- | :--- | :--- | :--- |
                                        | zhou | pm | 11 | led platform delivery |

                                        ## quote list
                                        | no | item | qty | unit price | total |
                                        | :--- | :--- | :--- | :--- | :--- |
                                        | 1 | migration service | 1 | 102000 | 102000 |
                                        | 2 | storage service | 1 | 202000 | 202000 |
                                        | 3 | ops service | 1 | 302000 | 302000 |
                                        """
                        )
                )
        ));

        WorkflowResult result = workflow.execute(AgentContext.from(req));

        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.getAnswer().contains("Fusion score="));
        assertFalse(result.getEvidenceGroups().isEmpty());
        assertTrue(result.getEvidenceGroups().stream().anyMatch(group -> "risk_fusion".equals(group.getGroupKey())));
    }
}
