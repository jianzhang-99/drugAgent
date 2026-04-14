package com.liang.drugagent.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeReq;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeResp;
import com.liang.drugagent.scene.tender_review.service.TenderSemanticReviewService;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenderSemanticReviewService 语义裁决服务单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>正常JSON解析</li>
 *   <li>降级响应处理</li>
 *   <li>JSON解析失败时的重试逻辑</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TenderSemanticReviewServiceTest {

    @Mock
    private LlmService llmService;

    private ObjectMapper objectMapper;

    private TenderSemanticReviewService tenderSemanticReviewService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 使用真实单线程Executor避免异步问题
        ExecutorService executor = Executors.newSingleThreadExecutor();
        tenderSemanticReviewService = new TenderSemanticReviewService(
                llmService, objectMapper, executor);
    }

    /**
     * 测试：正常JSON解析 - LLM返回有效JSON
     */
    @Test
    void should正常JSON解析返回结构化结果() throws Exception {
        String jsonContent = """
                {
                    "hit": true,
                    "ruleCode": "W-M2",
                    "riskType": "collusion",
                    "confidence": 0.85,
                    "suggestedWeight": 80,
                    "conclusion": "存在联系方式近邻",
                    "reason": "双方联系人手机号相同",
                    "evidences": [],
                    "cautionNotes": []
                }
                """;

        LlmResponse successResponse = LlmResponse.builder()
                .success(true)
                .content(jsonContent)
                .build();

        // 同步调用mock
        when(llmService.chat(any())).thenReturn(successResponse);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("联系人：张三 13800138000"))
                .rightSnippets(List.of("联系人：张三 13800138000"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        assertNotNull(result);
        assertEquals("W-M2", result.getRuleCode());
        assertTrue(result.getHit());
        assertEquals(0.85, result.getConfidence());
    }

    /**
     * 测试：降级响应处理 - LLM调用失败返回低置信度结果
     */
    @Test
    void shouldLLM调用失败时返回降级响应() throws Exception {
        LlmResponse failedResponse = LlmResponse.builder()
                .success(false)
                .errorCode("TIMEOUT")
                .errorMessage("LLM调用超时")
                .build();

        when(llmService.chat(any())).thenReturn(failedResponse);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        assertNotNull(result);
        assertFalse(result.getHit(), "降级响应应返回hit=false");
        assertEquals(0.3, result.getConfidence(), "降级响应置信度应为0.3");
        assertEquals(0, result.getSuggestedWeight(), "降级响应建议权重应为0");
    }

    /**
     * 测试：降级响应处理 - LLM返回null
     */
    @Test
    void shouldLLM返回Null时返回降级响应() throws Exception {
        when(llmService.chat(any())).thenReturn(null);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        assertNotNull(result);
        assertFalse(result.getHit());
        assertEquals(0.3, result.getConfidence());
    }

    /**
     * 测试：JSON解析失败时的重试逻辑
     */
    @Test
    void shouldJSON解析失败时进行重试() throws Exception {
        // 第一次返回非JSON内容，第二次返回有效JSON
        String invalidContent = "这是一些无效的LLM输出，不是JSON格式";
        String validContent = """
                {
                    "hit": true,
                    "ruleCode": "W-M2",
                    "confidence": 0.75,
                    "suggestedWeight": 70,
                    "conclusion": "测试结论",
                    "reason": "测试理由",
                    "evidences": [],
                    "cautionNotes": []
                }
                """;

        LlmResponse invalidResponse = LlmResponse.builder()
                .success(true)
                .content(invalidContent)
                .build();
        LlmResponse validResponse = LlmResponse.builder()
                .success(true)
                .content(validContent)
                .build();

        // 第一次返回无效内容，第二次返回有效内容（重试）
        when(llmService.chat(any()))
                .thenReturn(invalidResponse)
                .thenReturn(validResponse);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        // 重试后应得到有效结果
        assertNotNull(result);
        verify(llmService, atLeast(2)).chat(any());
    }

    /**
     * 测试：重试后仍失败则返回降级响应
     */
    @Test
    void should重试后仍失败则返回降级响应() throws Exception {
        // 两次都返回无法解析的内容
        String invalidContent = "无效的LLM输出内容";

        LlmResponse invalidResponse = LlmResponse.builder()
                .success(true)
                .content(invalidContent)
                .build();

        when(llmService.chat(any())).thenReturn(invalidResponse);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        // 由于JSON解析会失败两次，最终应返回降级响应
        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        assertNotNull(result);
        // 最终应返回降级响应
        assertEquals(0.3, result.getConfidence());
    }

    /**
     * 测试：空内容返回降级响应
     */
    @Test
    void should空内容返回降级响应() throws Exception {
        LlmResponse emptyResponse = LlmResponse.builder()
                .success(true)
                .content("")
                .build();

        when(llmService.chat(any())).thenReturn(emptyResponse);

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        TenderSemanticJudgeResp result = tenderSemanticReviewService.judge(req);

        assertNotNull(result);
        assertFalse(result.getHit());
        assertEquals(0.3, result.getConfidence());
    }

    /**
     * 测试：异常时返回降级响应
     */
    @Test
    void shouldLLM异常时返回降级响应() throws Exception {
        when(llmService.chat(any())).thenThrow(new RuntimeException("LLM服务异常"));

        TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                .caseId("case-1")
                .ruleCode("W-M2")
                .compareTopic("联系人方式比对")
                .leftSnippets(List.of("片段1"))
                .rightSnippets(List.of("片段2"))
                .leftDocumentId("doc-1")
                .rightDocumentId("doc-2")
                .build();

        // 由于RuntimeException被catch后返回降级响应，不应抛出异常
        TenderSemanticJudgeResp result = assertDoesNotThrow(() -> tenderSemanticReviewService.judge(req));

        assertNotNull(result);
        assertFalse(result.getHit());
        assertEquals(0.3, result.getConfidence());
    }
}
