package com.liang.drugagent.shared.tool;

import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.rag.adapter.RagOutcomeAdapter;
import com.liang.drugagent.shared.rag.model.RagQueryRequest;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import com.liang.drugagent.shared.rag.model.RagDecision;
import com.liang.drugagent.shared.rag.model.RagReason;
import com.liang.drugagent.shared.rag.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KnowledgeRetrievalTool 单元测试。
 *
 * <p>职责边界测试：
 * <ul>
 *   <li>retrieve() 方法 - 完整检索（生成回答）</li>
 *   <li>search() 方法 - 仅检索（不生成回答）</li>
 *   <li>空结果降级处理</li>
 *   <li>异常降级处理</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("知识检索工具测试")
class KnowledgeRetrievalToolTest {

    @Mock
    private RagService ragService;

    @Mock
    private RagOutcomeAdapter ragOutcomeAdapter;

    private KnowledgeRetrievalTool knowledgeRetrievalTool;

    @BeforeEach
    void setUp() {
        knowledgeRetrievalTool = new KnowledgeRetrievalTool(ragService, ragOutcomeAdapter);
    }

    // ========== retrieve() 方法测试 ==========

    @Test
    @DisplayName("shouldRetrieveSuccessfullyWhenValidParams")
    void shouldRetrieveSuccessfullyWhenValidParams() {
        String question = "什么是串通投标？";
        String orgId = "org-001";
        String scene = "tender_review";

        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.ANSWERED)
                .reason(RagReason.HIT)
                .answer("串通投标是违法行为。")
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("HIT")
                .reason("HIT")
                .answer("串通投标是违法行为。")
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.retrieve(question, orgId, scene);

        assertNotNull(result);
        assertEquals("HIT", result.getDecision());
        assertEquals("串通投标是违法行为。", result.getAnswer());

        ArgumentCaptor<RagQueryRequest> requestCaptor = ArgumentCaptor.forClass(RagQueryRequest.class);
        verify(ragService).query(requestCaptor.capture());

        RagQueryRequest capturedRequest = requestCaptor.getValue();
        assertEquals(question, capturedRequest.getQuestion());
        assertEquals(orgId, capturedRequest.getOrgId());
        assertEquals(scene, capturedRequest.getScene());
        assertTrue(capturedRequest.getNeedGenerateAnswer());
    }

    @Test
    @DisplayName("shouldReturnNoHitWhenOrgIdMissing")
    void shouldReturnNoHitWhenOrgIdMissing() {
        RagOutcome result = knowledgeRetrievalTool.retrieve("测试问题", null, "tender_review");

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("CONTEXT_MISSING", result.getReason());
        assertEquals("检索失败：缺少组织标识", result.getAnswer());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldReturnNoHitWhenOrgIdBlank")
    void shouldReturnNoHitWhenOrgIdBlank() {
        RagOutcome result = knowledgeRetrievalTool.retrieve("测试问题", "  ", "tender_review");

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("CONTEXT_MISSING", result.getReason());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldReturnNoCandidateWhenQuestionMissing")
    void shouldReturnNoCandidateWhenQuestionMissing() {
        RagOutcome result = knowledgeRetrievalTool.retrieve(null, "org-001", "tender_review");

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("NO_CANDIDATE", result.getReason());
        assertEquals("检索失败：问题内容为空", result.getAnswer());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldReturnNoCandidateWhenQuestionBlank")
    void shouldReturnNoCandidateWhenQuestionBlank() {
        RagOutcome result = knowledgeRetrievalTool.retrieve("", "org-001", "tender_review");

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("NO_CANDIDATE", result.getReason());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldRetrieveWithAllParameters")
    void shouldRetrieveWithAllParameters() {
        String question = "测试问题";
        String orgId = "org-001";
        String scene = "tender_review";
        String subScene = "W-M1";
        String docType = "REGULATION";
        List<String> topicTags = List.of("pricing", "collusion");
        Integer topK = 10;
        Boolean needGenerateAnswer = true;

        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.ANSWERED)
                .reason(RagReason.HIT)
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("HIT")
                .reason("HIT")
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.retrieve(
                question, orgId, scene, subScene, docType, topicTags, topK, needGenerateAnswer
        );

        assertNotNull(result);

        ArgumentCaptor<RagQueryRequest> requestCaptor = ArgumentCaptor.forClass(RagQueryRequest.class);
        verify(ragService).query(requestCaptor.capture());

        RagQueryRequest capturedRequest = requestCaptor.getValue();
        assertEquals(question, capturedRequest.getQuestion());
        assertEquals(orgId, capturedRequest.getOrgId());
        assertEquals(scene, capturedRequest.getScene());
        assertEquals(subScene, capturedRequest.getSubScene());
        assertEquals(docType, capturedRequest.getDocType());
        assertEquals(topicTags, capturedRequest.getTopicTags());
        assertEquals(topK, capturedRequest.getTopK());
        assertEquals(needGenerateAnswer, capturedRequest.getNeedGenerateAnswer());
    }

    @Test
    @DisplayName("shouldUseDefaultTopKWhenNull")
    void shouldUseDefaultTopKWhenNull() {
        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.ANSWERED)
                .reason(RagReason.HIT)
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("HIT")
                .reason("HIT")
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        knowledgeRetrievalTool.retrieve("测试问题", "org-001", "tender_review", null, null, null, null, null);

        ArgumentCaptor<RagQueryRequest> requestCaptor = ArgumentCaptor.forClass(RagQueryRequest.class);
        verify(ragService).query(requestCaptor.capture());

        assertEquals(5, requestCaptor.getValue().getTopK()); // 默认值
    }

    @Test
    @DisplayName("shouldDegradeToNeedHumanReviewWhenExceptionOccurs")
    void shouldDegradeToNeedHumanReviewWhenExceptionOccurs() {
        when(ragService.query(any(RagQueryRequest.class)))
                .thenThrow(new RuntimeException("Vector store connection failed"));

        RagOutcome result = knowledgeRetrievalTool.retrieve("测试问题", "org-001", "tender_review");

        assertNotNull(result);
        assertEquals("NEED_HUMAN_REVIEW", result.getDecision());
        assertEquals("LOW_CONFIDENCE", result.getReason());
        assertTrue(result.getAnswer().contains("检索过程中发生异常"));
    }

    // ========== search() 方法测试 ==========

    @Test
    @DisplayName("shouldSearchSuccessfullyWithoutLLMGeneration")
    void shouldSearchSuccessfullyWithoutLLMGeneration() {
        String question = "串通投标的法律后果";
        String orgId = "org-001";
        String scene = "tender_review";
        String docType = "REGULATION";
        Integer topK = 3;

        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.ANSWERED)
                .reason(RagReason.HIT)
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("HIT")
                .reason("HIT")
                .evidenceList(List.of())
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.search(question, orgId, scene, docType, topK);

        assertNotNull(result);
        assertEquals("HIT", result.getDecision());

        ArgumentCaptor<RagQueryRequest> requestCaptor = ArgumentCaptor.forClass(RagQueryRequest.class);
        verify(ragService).query(requestCaptor.capture());

        RagQueryRequest capturedRequest = requestCaptor.getValue();
        assertEquals(question, capturedRequest.getQuestion());
        assertEquals(orgId, capturedRequest.getOrgId());
        assertEquals(scene, capturedRequest.getScene());
        assertEquals(docType, capturedRequest.getDocType());
        assertEquals(topK, capturedRequest.getTopK());
        assertFalse(capturedRequest.getNeedGenerateAnswer()); // 关键：search 不生成回答
    }

    @Test
    @DisplayName("shouldReturnNoHitWhenOrgIdMissingInSearch")
    void shouldReturnNoHitWhenOrgIdMissingInSearch() {
        RagOutcome result = knowledgeRetrievalTool.search("测试问题", null, "tender_review", null, null);

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("CONTEXT_MISSING", result.getReason());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldReturnNoCandidateWhenQuestionBlankInSearch")
    void shouldReturnNoCandidateWhenQuestionBlankInSearch() {
        RagOutcome result = knowledgeRetrievalTool.search("   ", "org-001", "tender_review", null, null);

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
        assertEquals("NO_CANDIDATE", result.getReason());

        verify(ragService, never()).query(any());
    }

    @Test
    @DisplayName("shouldHandleSearchExceptionGracefully")
    void shouldHandleSearchExceptionGracefully() {
        when(ragService.query(any(RagQueryRequest.class)))
                .thenThrow(new RuntimeException("Database connection error"));

        RagOutcome result = knowledgeRetrievalTool.search("测试问题", "org-001", "tender_review", null, null);

        assertNotNull(result);
        assertEquals("NEED_HUMAN_REVIEW", result.getDecision());
        assertTrue(result.getAnswer().contains("异常"));
    }

    // ========== 空结果降级测试 ==========

    @Test
    @DisplayName("shouldReturnNoHitWhenRagServiceReturnsNull")
    void shouldReturnNoHitWhenRagServiceReturnsNull() {
        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.NO_HIT)
                .reason(RagReason.NO_CANDIDATE)
                .answer("未找到相关知识片段")
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("NO_HIT")
                .reason("NO_CANDIDATE")
                .evidenceList(List.of())
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.retrieve("不相关的问题", "org-001", "tender_review");

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
    }

    @Test
    @DisplayName("shouldReturnNoHitWhenNoEvidenceFound")
    void shouldReturnNoHitWhenNoEvidenceFound() {
        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.NO_HIT)
                .reason(RagReason.NO_CANDIDATE)
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("NO_HIT")
                .reason("NO_CANDIDATE")
                .evidenceList(List.of())
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.search("完全不相关的问题", "org-001", "tender_review", null, null);

        assertNotNull(result);
        assertEquals("NO_HIT", result.getDecision());
    }

    @Test
    @DisplayName("shouldReturnNeedHumanReviewWhenLowConfidence")
    void shouldReturnNeedHumanReviewWhenLowConfidence() {
        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(RagDecision.NEED_HUMAN_REVIEW)
                .reason(RagReason.LOW_CONFIDENCE)
                .answer("检索到一些片段，但无法生成可靠回答")
                .needHumanReview(true)
                .build();

        RagOutcome mockOutcome = RagOutcome.builder()
                .decision("NEED_HUMAN_REVIEW")
                .reason("LOW_CONFIDENCE")
                .needHumanReview(true)
                .build();

        when(ragService.query(any(RagQueryRequest.class))).thenReturn(mockResponse);
        when(ragOutcomeAdapter.toRagOutcome(any())).thenReturn(mockOutcome);

        RagOutcome result = knowledgeRetrievalTool.retrieve("模糊的非常见问题", "org-001", "tender_review");

        assertNotNull(result);
        assertEquals("NEED_HUMAN_REVIEW", result.getDecision());
    }
}