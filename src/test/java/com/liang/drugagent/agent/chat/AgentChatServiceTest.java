package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceTest {

    @Mock
    private AgentSceneService agentSceneService;

    @Mock
    private AgentSessionService agentSessionService;

    @Mock
    private AgentResponseService agentResponseService;

    @Mock
    private AgentMessageService agentMessageService;

    @Mock
    private TencentCosStorageService cosStorageService;

    @Captor
    private ArgumentCaptor<AgentChatContext> contextCaptor;

    @Captor
    private ArgumentCaptor<AgentExecutionResult> executionResultCaptor;

    @Captor
    private ArgumentCaptor<String> metadataCaptor;

    private AgentChatService agentChatService;

    @BeforeEach
    void setUp() {
        agentChatService = new AgentChatService(
                agentSceneService,
                agentSessionService,
                agentResponseService,
                agentMessageService,
                cosStorageService
        );
    }

    @Test
    void shouldMergeUploadedFileIdsAndPersistStructuredResultCard() {
        MultipartFile[] files = new MultipartFile[]{mock(MultipartFile.class)};

        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-success")
                .query("Please review the two tenders.")
                .fileIds(new ArrayList<>(List.of("existing-file", "oss-file-1")))
                .files(files)
                .build();

        ChatSession session = ChatSession.builder()
                .id("session-success")
                .summary("previous summary")
                .build();

        OssFile uploadedFile = OssFile.builder()
                .id("oss-file-1")
                .fileName("tender-a.pdf")
                .build();

        WorkflowRouteDecision decision = WorkflowRouteDecision.builder()
                .scene(SceneEnum.TENDER_REVIEW)
                .source("rule-strong")
                .reason("matched tender keywords")
                .confidence(0.95)
                .requiresClarification(false)
                .build();

        AgentExecutionResult executionResult = AgentExecutionResult.builder()
                .success(true)
                .scene(SceneEnum.TENDER_REVIEW)
                .answer("final answer")
                .summary("summary text")
                .riskLevel("HIGH")
                .score(87)
                .generatedTitle("Tender review title")
                .shouldUpdateTitle(true)
                .build();

        when(agentSessionService.getOrCreateSession("session-success")).thenReturn(session);
        when(agentMessageService.getRecentMessages("session-success", 20)).thenReturn(List.of());
        when(cosStorageService.saveUploadedFiles("session-success", files)).thenReturn(List.of(uploadedFile));
        when(agentSceneService.decideAndExecute(any(), same(req))).thenReturn(
                AgentSceneService.AgentSceneExecution.builder()
                        .decision(decision)
                        .executionResult(executionResult)
                        .needsClarification(false)
                        .build()
        );
        when(agentResponseService.buildResponse(any(), any(), any())).thenAnswer(invocation -> {
            AgentChatContext context = invocation.getArgument(0);
            WorkflowRouteDecision routeDecision = invocation.getArgument(1);
            AgentExecutionResult result = invocation.getArgument(2);

            return AgentChatResp.builder()
                    .sessionId(context.getSessionId())
                    .traceId(context.getTraceId())
                    .scene(routeDecision != null && routeDecision.getScene() != null
                            ? routeDecision.getScene().name()
                            : SceneEnum.UNKNOWN.name())
                    .routeReason(routeDecision != null ? routeDecision.getReason() : null)
                    .routeSource(routeDecision != null ? routeDecision.getSource() : null)
                    .confidence(routeDecision != null ? routeDecision.getConfidence() : null)
                    .answer(result != null ? result.getAnswer() : null)
                    .summary(result != null ? result.getSummary() : null)
                    .riskLevel(result != null ? result.getRiskLevel() : null)
                    .score(result != null && result.getScore() != null ? result.getScore() : 0)
                    .sessionTitle(result != null ? result.getGeneratedTitle() : null)
                    .requiresClarification(result != null && result.isNeedsFallback())
                    .clarificationQuestion(result != null ? result.getClarificationQuestion() : null)
                    .build();
        });

        AgentChatResp resp = agentChatService.chat(req);

        assertNotNull(resp);
        assertEquals("session-success", resp.getSessionId());
        assertEquals(SceneEnum.TENDER_REVIEW.name(), resp.getScene());
        assertEquals("final answer", resp.getAnswer());
        assertEquals("summary text", resp.getSummary());
        assertEquals("HIGH", resp.getRiskLevel());
        assertEquals("Tender review title", resp.getSessionTitle());

        assertEquals(List.of("existing-file", "oss-file-1"), req.getFileIds());

        verify(cosStorageService).saveUploadedFiles("session-success", files);
        verify(agentSceneService).decideAndExecute(contextCaptor.capture(), same(req));
        verify(agentMessageService).saveUserMessage("session-success", "Please review the two tenders.", null);
        verify(agentMessageService).saveAssistantMessage(
                eq("session-success"),
                eq("final answer"),
                metadataCaptor.capture(),
                eq("assistant_result_card")
        );
        verify(agentSessionService).touchSession("session-success", SceneEnum.TENDER_REVIEW.name());
        verify(agentSessionService).increaseMessageCount("session-success", 2);
        verify(agentSessionService).updateSessionTitleIfNeeded("session-success", "Tender review title");
        verify(agentSessionService).updateSessionSummary("session-success", "summary text");

        AgentChatContext context = contextCaptor.getValue();
        assertNotNull(context);
        assertEquals("session-success", context.getSessionId());
        assertEquals("previous summary", context.getRecentSummary());
        assertEquals(1, context.getUploadedFiles().size());
        assertEquals("oss-file-1", context.getUploadedFiles().get(0).getId());
        assertEquals(List.of("existing-file", "oss-file-1"), context.getFileIds());

        String metadataJson = metadataCaptor.getValue();
        assertNotNull(metadataJson);
        assertTrue(metadataJson.contains("\"sessionId\":\"session-success\""));
        assertTrue(metadataJson.contains("\"scene\":\"TENDER_REVIEW\""));
        assertTrue(metadataJson.contains("\"riskLevel\":\"HIGH\""));

        verify(agentResponseService, times(2)).buildResponse(any(), any(), any());
    }

    @Test
    void shouldReturnClarificationWithoutSavingAssistantMessage() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-clarify")
                .query("Need help with these files")
                .build();

        ChatSession session = ChatSession.builder()
                .id("session-clarify")
                .build();

        WorkflowRouteDecision decision = WorkflowRouteDecision.builder()
                .scene(SceneEnum.TENDER_REVIEW)
                .source("rule-weak")
                .reason("needs clarification")
                .confidence(0.5)
                .requiresClarification(true)
                .clarificationQuestion("Need two files")
                .build();

        when(agentSessionService.getOrCreateSession("session-clarify")).thenReturn(session);
        when(agentMessageService.getRecentMessages("session-clarify", 20)).thenReturn(List.of());
        when(agentSceneService.decideAndExecute(any(), same(req))).thenReturn(
                AgentSceneService.AgentSceneExecution.builder()
                        .decision(decision)
                        .executionResult(AgentExecutionResult.builder()
                                .success(false)
                                .scene(SceneEnum.TENDER_REVIEW)
                                .needsFallback(true)
                                .clarificationQuestion("Need two files")
                                .build())
                        .needsClarification(true)
                        .clarificationQuestion("Need two files")
                        .build()
        );
        when(agentResponseService.buildResponse(any(), any(), any())).thenAnswer(invocation -> {
            AgentChatContext context = invocation.getArgument(0);
            WorkflowRouteDecision routeDecision = invocation.getArgument(1);
            AgentExecutionResult result = invocation.getArgument(2);

            return AgentChatResp.builder()
                    .sessionId(context.getSessionId())
                    .scene(routeDecision.getScene().name())
                    .routeSource(routeDecision.getSource())
                    .routeReason(routeDecision.getReason())
                    .requiresClarification(result.isNeedsFallback())
                    .clarificationQuestion(result.getClarificationQuestion())
                    .answer(result.getAnswer())
                    .build();
        });

        AgentChatResp resp = agentChatService.chat(req);

        assertNotNull(resp);
        assertTrue(resp.isRequiresClarification());
        assertEquals("Need two files", resp.getClarificationQuestion());
        assertEquals(SceneEnum.TENDER_REVIEW.name(), resp.getScene());

        verify(agentMessageService).saveUserMessage("session-clarify", "Need help with these files", null);
        verify(agentMessageService, never()).saveAssistantMessage(anyString(), anyString(), any(), anyString());
        verify(agentSessionService).increaseMessageCount("session-clarify", 1);
        verify(agentSessionService, never()).touchSession(anyString(), anyString());

        verify(agentResponseService).buildResponse(any(), any(), executionResultCaptor.capture());
        AgentExecutionResult clarificationResult = executionResultCaptor.getValue();
        assertNotNull(clarificationResult);
        assertFalse(clarificationResult.isSuccess());
        assertTrue(clarificationResult.isNeedsFallback());
        assertEquals(SceneEnum.TENDER_REVIEW, clarificationResult.getScene());
        assertEquals("Need two files", clarificationResult.getClarificationQuestion());
    }

    @Test
    void shouldFallbackWhenFileUploadFailsBeforeRouting() {
        MultipartFile[] files = new MultipartFile[]{mock(MultipartFile.class)};

        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-upload-fail")
                .query("Please review this tender")
                .files(files)
                .build();

        ChatSession session = ChatSession.builder()
                .id("session-upload-fail")
                .build();

        when(agentSessionService.getOrCreateSession("session-upload-fail")).thenReturn(session);
        when(cosStorageService.saveUploadedFiles("session-upload-fail", files))
                .thenThrow(new RuntimeException("upload failed"));

        AgentChatResp resp = agentChatService.chat(req);

        assertNotNull(resp);
        assertEquals(SceneEnum.UNKNOWN.name(), resp.getScene());
        assertEquals("UNKNOWN", resp.getRiskLevel());
        assertTrue(resp.isRequiresClarification());
        assertNotNull(resp.getClarificationQuestion());

        verify(cosStorageService).saveUploadedFiles("session-upload-fail", files);
        verify(agentSceneService, never()).decideAndExecute(any(), any());
        verify(agentMessageService, never()).saveUserMessage(anyString(), anyString(), any());
        verify(agentMessageService, never()).saveAssistantMessage(anyString(), anyString(), any(), anyString());
        verifyNoInteractions(agentResponseService);
    }
}
