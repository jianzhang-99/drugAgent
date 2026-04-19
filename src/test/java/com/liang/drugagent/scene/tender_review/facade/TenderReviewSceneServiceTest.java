package com.liang.drugagent.scene.tender_review.facade;

import com.liang.drugagent.agent.chat.AgentMessageService;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.preparation.TenderReviewPreparationService;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderReviewSceneServiceTest {

    @Mock
    private TenderReviewPreparationService preparationService;

    @Mock
    private TenderReviewWorkflow tenderReviewWorkflow;

    @Mock
    private LlmService llmService;

    @Mock
    private AgentMessageService agentMessageService;

    private TenderReviewSceneService sceneService;

    @BeforeEach
    void setUp() {
        sceneService = new TenderReviewSceneService(
                preparationService,
                tenderReviewWorkflow,
                llmService,
                agentMessageService
        );
    }

    @Test
    void shouldAnswerFromHistoryReportInsteadOfRerunningWorkflowWhenFollowUpQuestionArrives() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-followup")
                .query("Is the tender fully compliant?")
                .fileIds(List.of("file-1", "file-2"))
                .build();

        AgentChatContext context = AgentChatContext.from(req, "session-followup");
        context.setSession(ChatSession.builder()
                .id("session-followup")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());
        context.setHistoryMessages(List.of(
                ChatMessage.builder()
                        .role("LLM")
                        .type("assistant_result_card")
                        .content("Tender review report\n\nConclusion: several risks were found.")
                        .build()
        ));

        when(llmService.chat(any())).thenReturn(
                LlmResponse.builder()
                        .success(true)
                        .provider(LlmProviderType.DASHSCOPE)
                        .content("Based on the history report, it cannot be considered fully compliant.")
                        .build()
        );

        AgentExecutionResult result = sceneService.execute(context, req);

        assertNotNull(result);
        assertEquals(SceneEnum.TENDER_REVIEW, result.getScene());
        assertEquals("Based on the history report, it cannot be considered fully compliant.", result.getAnswer());
        assertEquals("基于审查报告追问解答", result.getSummary());
        assertFalse(result.isNeedsFallback());
        assertNull(result.getReport());

        verifyNoInteractions(preparationService, tenderReviewWorkflow);
    }
}
