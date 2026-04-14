package com.liang.drugagent.scene.tender_review.facade;

import com.liang.drugagent.agent.chat.AgentMessageService;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.preparation.TenderReviewPreparationService;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.ThinkingStep;
import com.liang.drugagent.shared.model.ThinkingStepEmitter;
import com.liang.drugagent.shared.model.ThinkingStepProgress;
import com.liang.drugagent.shared.model.WorkflowResult;
import com.liang.drugagent.shared.rag.entity.OssFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderReviewSceneServiceStreamTest {

    @Mock
    private TenderReviewPreparationService preparationService;

    @Mock
    private TenderReviewWorkflow tenderReviewWorkflow;

    @Mock
    private LlmService llmService;

    @Mock
    private AgentMessageService agentMessageService;

    @Test
    void shouldEmitPreparationProgressAndMergeStreamResultMetadata() {
        TenderReviewSceneService sceneService = new TenderReviewSceneService(
                preparationService,
                tenderReviewWorkflow,
                llmService,
                agentMessageService
        );

        AgentChatReq req = AgentChatReq.builder()
                .query("请审查这两份标书")
                .fileIds(List.of("oss-file-1", "oss-file-2"))
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-stream-1");
        context.setUploadedFiles(List.of(
                OssFile.builder().id("oss-file-1").fileName("投标文件A.docx").build(),
                OssFile.builder().id("oss-file-2").fileName("投标文件B.docx").build()
        ));

        TenderReviewData data = new TenderReviewData();
        data.setDocuments(List.of(
                TenderDocument.builder().documentId("doc-a").documentName("投标文件A.docx").build(),
                TenderDocument.builder().documentId("doc-b").documentName("投标文件B.docx").build()
        ));

        when(preparationService.prepare(context, req)).thenReturn(data);
        when(preparationService.hasEnoughDocuments(data)).thenReturn(true);
        when(tenderReviewWorkflow.executeWithProgress(any(AgentChatContext.class), any(ThinkingStepEmitter.class)))
                .thenAnswer(invocation -> {
                    ThinkingStepEmitter emitter = invocation.getArgument(1);

                    ThinkingStep workflowStep = ThinkingStep.builder()
                            .code("rule_analysis")
                            .title("规则命中分析")
                            .detail("正在执行确定性规则检测")
                            .type("EXECUTION")
                            .status("PROCESSING")
                            .order(3)
                            .build();

                    emitter.emit(ThinkingStepProgress.builder()
                            .currentCode(workflowStep.getCode())
                            .currentTitle(workflowStep.getTitle())
                            .currentStatus(workflowStep.getStatus())
                            .currentDetail(workflowStep.getDetail())
                            .currentStep(workflowStep)
                            .completedSteps(List.of())
                            .finalResult(false)
                            .build());

                    ThinkingStep completedWorkflowStep = ThinkingStep.builder()
                            .code("rule_analysis")
                            .title("规则命中分析")
                            .detail("规则命中分析完成")
                            .type("EXECUTION")
                            .status("COMPLETED")
                            .order(3)
                            .build();
                    WorkflowResult workflowResult = WorkflowResult.of(SceneEnum.TENDER_REVIEW, "审查完成");
                    workflowResult.setSummary("发现明显围标特征");
                    workflowResult.setRiskLevel("high");
                    workflowResult.setScore(92);
                    workflowResult.setThinkingSteps(List.of(completedWorkflowStep));
                    workflowResult.setDocumentIds(List.of("doc-a", "doc-b"));
                    workflowResult.setSessionTitle("投标文件A与投标文件B审查");

                    emitter.emit(ThinkingStepProgress.builder()
                            .currentCode(completedWorkflowStep.getCode())
                            .currentTitle(completedWorkflowStep.getTitle())
                            .currentStatus(completedWorkflowStep.getStatus())
                            .currentDetail(completedWorkflowStep.getDetail())
                            .currentStep(completedWorkflowStep)
                            .completedSteps(List.of(completedWorkflowStep))
                            .finalResult(true)
                            .result(workflowResult)
                            .sessionTitle(workflowResult.getSessionTitle())
                            .documentIds(workflowResult.getDocumentIds())
                            .build());
                    return workflowResult;
                });

        List<ThinkingStepProgress> events = sceneService.streamExecute(context, req)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertTrue(events.size() >= 5);

        assertEquals("route", events.get(0).getCurrentCode());
        assertEquals("prepare_data", events.get(1).getCurrentCode());
        assertEquals("PROCESSING", events.get(1).getCurrentStatus());
        assertEquals("prepare_data", events.get(2).getCurrentCode());
        assertEquals("COMPLETED", events.get(2).getCurrentStatus());
        assertEquals("rule_analysis", events.get(3).getCurrentCode());

        ThinkingStepProgress finalEvent = events.get(events.size() - 1);
        assertTrue(finalEvent.isFinalResult());
        assertNotNull(finalEvent.getResult());
        assertEquals(List.of("oss-file-1", "oss-file-2"), finalEvent.getDocumentIds());
        assertEquals(List.of("oss-file-1", "oss-file-2"), finalEvent.getResult().getDocumentIds());
        assertEquals(List.of("投标文件A.docx", "投标文件B.docx"), finalEvent.getResult().getDocumentNames());
        assertEquals(context.getTraceId(), finalEvent.getResult().getTraceId());
        assertEquals(
                List.of("route", "prepare_data", "rule_analysis"),
                finalEvent.getResult().getThinkingSteps().stream().map(ThinkingStep::getCode).toList()
        );
    }

    @Test
    void shouldEmitFailedPreparationAsFinalResult() {
        TenderReviewSceneService sceneService = new TenderReviewSceneService(
                preparationService,
                tenderReviewWorkflow,
                llmService,
                agentMessageService
        );

        AgentChatReq req = AgentChatReq.builder()
                .query("请审查这两份标书")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-stream-2");
        context.getMetadata().put("preparationError", "只检测到 1 份有效标书文件");

        when(preparationService.prepare(context, req)).thenReturn(null);
        when(preparationService.hasEnoughDocuments(null)).thenReturn(false);

        List<ThinkingStepProgress> events = sceneService.streamExecute(context, req)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertEquals(3, events.size());
        ThinkingStepProgress finalEvent = events.get(2);
        assertTrue(finalEvent.isFinalResult());
        assertEquals("prepare_data", finalEvent.getCurrentCode());
        assertEquals("FAILED", finalEvent.getCurrentStatus());
        assertEquals("只检测到 1 份有效标书文件", finalEvent.getCurrentDetail());
        assertNull(finalEvent.getResult());
    }
}
