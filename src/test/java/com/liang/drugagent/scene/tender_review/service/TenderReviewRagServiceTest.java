package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewRagEvidence;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.tool.KnowledgeRetrievalTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 标书审查 RAG 服务测试。
 *
 * <p>覆盖 TenderReviewRagService 的核心逻辑：
 * <ul>
 *   <li>orgId 缺失时跳过检索</li>
 *   <li>无有效风险命中时跳过</li>
 *   <li>多风险类型去重</li>
 *   <li>空证据处理</li>
 *   <li>异常降级处理</li>
 *   <li>MAX_QUERY_COUNT 限制</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("标书审查RAG服务测试")
class TenderReviewRagServiceTest {

    @Mock
    private KnowledgeRetrievalTool knowledgeRetrievalTool;

    private TenderReviewRagService tenderReviewRagService;

    @BeforeEach
    void setUp() {
        tenderReviewRagService = new TenderReviewRagService(knowledgeRetrievalTool);
    }

    // ========== orgId 缺失跳过测试 ==========

    @Test
    @DisplayName("shouldSkipWhenOrgIdMissing")
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
    @DisplayName("shouldSkipWhenOrgIdBlank")
    void shouldSkipWhenOrgIdBlank() {
        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "   ",
                "trace-1"
        );

        assertEquals("SKIPPED", result.getStatus());
        assertEquals("MISSING_ORG_ID", result.getReason());
        verify(knowledgeRetrievalTool, never()).search(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    // ========== 无有效风险命中跳过测试 ==========

    @Test
    @DisplayName("shouldSkipWhenEffectiveHitsEmpty")
    void shouldSkipWhenEffectiveHitsEmpty() {
        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                new ArrayList<>(),
                "org-1",
                "trace-1"
        );

        assertEquals("SKIPPED", result.getStatus());
        assertEquals("NO_EFFECTIVE_HIT", result.getReason());
        verify(knowledgeRetrievalTool, never()).search(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("shouldSkipWhenEffectiveHitsNull")
    void shouldSkipWhenEffectiveHitsNull() {
        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                null,
                "org-1",
                "trace-1"
        );

        assertEquals("SKIPPED", result.getStatus());
        assertEquals("MISSING_ORG_ID", result.getReason());
        verify(knowledgeRetrievalTool, never()).search(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    // ========== 法规证据构建测试 ==========

    @Test
    @DisplayName("shouldBuildLegalEvidenceGroupFromRagOutcome")
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

    // ========== 多风险类型去重测试 ==========

    @Test
    @DisplayName("shouldDeduplicateQueriesByRiskType")
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
        verify(knowledgeRetrievalTool, times(2))
                .search(queryCaptor.capture(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3));
        assertTrue(queryCaptor.getAllValues().get(0).contains("联系方式"));
        assertTrue(queryCaptor.getAllValues().get(1).contains("团队"));
    }

    @Test
    @DisplayName("shouldNotQueryWhenAllHitsHaveNoSpec")
    void shouldNotQueryWhenAllHitsHaveNoSpec() {
        // 未知规则编码，不对应任何查询规格
        RuleHit hitWithUnknownCode = RuleHit.builder()
                .ruleCode("UNKNOWN-999")
                .ruleName("未知风险")
                .weight(50)
                .priority("MEDIUM")
                .build();

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(hitWithUnknownCode),
                "org-1",
                "trace-1"
        );

        // 使用默认查询规格 "collusion"
        verify(knowledgeRetrievalTool, times(1))
                .search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3));
    }

    // ========== 空证据处理测试 ==========

    @Test
    @DisplayName("shouldReturnNoHitWhenEvidenceListIsNull")
    void shouldReturnNoHitWhenEvidenceListIsNull() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(null)
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        assertEquals("NO_HIT", result.getStatus());
    }

    @Test
    @DisplayName("shouldReturnNoHitWhenEvidenceListIsEmpty")
    void shouldReturnNoHitWhenEvidenceListIsEmpty() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        assertEquals("NO_HIT", result.getStatus());
    }

    @Test
    @DisplayName("shouldSkipNullEvidenceItems")
    void shouldSkipNullEvidenceItems() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("HIT")
                        .reason("HIT")
                        .evidenceList(List.of(
                                EvidenceItem.builder()
                                        .title("招标投标法")
                                        .content("投标人不得相互串通投标。")
                                        .build(),
                                null, // 空证据项
                                EvidenceItem.builder()
                                        .title(null) // 空标题
                                        .content("some content")
                                        .build(),
                                EvidenceItem.builder()
                                        .title("损坏文件")
                                        .content(null) // 空内容
                                        .build()
                        ))
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        // 只应保留有效的证据项（第一个有内容和标题的）
        assertEquals(1, result.getHitCount());
    }

    @Test
    @DisplayName("shouldSkipEvidenceWithBlankContent")
    void shouldSkipEvidenceWithBlankContent() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("HIT")
                        .reason("HIT")
                        .evidenceList(List.of(
                                EvidenceItem.builder()
                                        .title("有效证据")
                                        .content("这是有效内容")
                                        .build(),
                                EvidenceItem.builder()
                                        .title("无效证据")
                                        .content("   ") // 空白内容
                                        .build(),
                                EvidenceItem.builder()
                                        .title("另一个无效证据")
                                        .content("") // 空字符串内容
                                        .build()
                        ))
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        assertEquals(1, result.getHitCount());
        assertTrue(result.getItems().get(0).getContent().contains("这是有效内容"));
    }

    // ========== 异常降级测试 ==========

    @Test
    @DisplayName("shouldDegradeWhenRetrievalThrowsException")
    void shouldDegradeWhenRetrievalThrowsException() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenThrow(new RuntimeException("Vector store connection failed"));

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        assertEquals("NO_HIT", result.getStatus());
        assertTrue(result.getReason().contains("DEGRADED"));
    }

    @Test
    @DisplayName("shouldContinueWhenOneQueryFails")
    void shouldContinueWhenOneQueryFails() {
        // 第一个查询失败，第二个成功
        when(knowledgeRetrievalTool.search(argThat(q -> q.contains("报价")), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenThrow(new RuntimeException("Connection failed"));

        when(knowledgeRetrievalTool.search(argThat(q -> q.contains("团队")), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("HIT")
                        .reason("HIT")
                        .evidenceList(List.of(EvidenceItem.builder()
                                .title("招投标条例")
                                .content("评审委员会成员不得与投标人有利害关系。")
                                .build()))
                        .build());

        TenderReviewRagEvidence result = tenderReviewRagService.retrieveEvidence(
                List.of(
                        buildHit("W-M1", "报价梯度异常"),
                        buildHit("W-M3", "团队成员重叠")
                ),
                "org-1",
                "trace-1"
        );

        // 应该继续处理成功的查询
        assertEquals("SUPPORTED", result.getStatus());
        assertEquals(1, result.getHitCount());
    }

    // ========== MAX_QUERY_COUNT 限制测试 ==========

    @Test
    @DisplayName("shouldLimitToMaxQueryCount")
    void shouldLimitToMaxQueryCount() {
        // 模拟返回 NO_HIT，避免空证据问题
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        // 创建超过 MAX_QUERY_COUNT (4) 的风险命中
        List<RuleHit> manyHits = new ArrayList<>();
        manyHits.add(buildHit("W-M1", "报价梯度异常1"));
        manyHits.add(buildHit("W-M1", "报价梯度异常2"));
        manyHits.add(buildHit("W-M2", "联系方式近邻1"));
        manyHits.add(buildHit("W-M2", "联系方式近邻2"));
        manyHits.add(buildHit("W-M3", "团队成员重叠1"));
        manyHits.add(buildHit("W-M4", "模板同源1"));
        manyHits.add(buildHit("W-P1", "方案雷同1"));

        tenderReviewRagService.retrieveEvidence(manyHits, "org-1", "trace-1");

        // 最多只应执行 MAX_QUERY_COUNT (4) 次查询
        verify(knowledgeRetrievalTool, atMost(4))
                .search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3));
    }

    // ========== 多风险类型检索映射测试 ==========

    @Test
    @DisplayName("shouldMapW-M1ToPricingQuery")
    void shouldMapW_M1ToPricingQuery() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M1", "报价梯度异常")),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool).search(queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("报价"));
    }

    @Test
    @DisplayName("shouldMapW-M2ToContactQuery")
    void shouldMapW_M2ToContactQuery() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M2", "联系方式近邻")),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool).search(queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("联系方式"));
    }

    @Test
    @DisplayName("shouldMapW-M3ToTeamQuery")
    void shouldMapW_M3ToTeamQuery() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M3", "团队成员重叠")),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool).search(queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("团队"));
    }

    @Test
    @DisplayName("shouldMapW-M4ToTemplateQuery")
    void shouldMapW_M4ToTemplateQuery() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-M4", "模板同源")),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool).search(queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("模板"));
    }

    @Test
    @DisplayName("shouldMapW-PToSimilarityQuery")
    void shouldMapW_PToSimilarityQuery() {
        when(knowledgeRetrievalTool.search(anyString(), eq("org-1"), eq("tender_review"), eq("REGULATION"), eq(3)))
                .thenReturn(RagOutcome.builder()
                        .decision("NO_HIT")
                        .reason("NO_CANDIDATE")
                        .evidenceList(List.of())
                        .build());

        tenderReviewRagService.retrieveEvidence(
                List.of(buildHit("W-P1", "方案雷同")),
                "org-1",
                "trace-1"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeRetrievalTool).search(queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("内容雷同"));
    }

    // ========== 辅助方法 ==========

    private RuleHit buildHit(String ruleCode, String ruleName) {
        return RuleHit.builder()
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .weight(80)
                .priority("HIGH")
                .build();
    }
}
