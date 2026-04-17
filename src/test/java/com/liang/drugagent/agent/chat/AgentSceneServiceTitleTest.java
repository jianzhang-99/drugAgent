package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.shared.contextcache.DashScopeContextCacheService;
import com.liang.drugagent.shared.intent.IntentDetectionService;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.scene.tender_review.facade.TenderReviewSceneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class AgentSceneServiceTitleTest {

    @Mock
    private LlmService llmService;

    @Mock
    private TenderReviewSceneService tenderReviewSceneService;

    @Mock
    private IntentDetectionService intentDetectionService;

    @Mock
    private DashScopeContextCacheService dashScopeContextCacheService;

    private AgentSceneService agentSceneService;

    @BeforeEach
    void setUp() {
        agentSceneService = new AgentSceneService(
                llmService,
                tenderReviewSceneService,
                intentDetectionService,
                dashScopeContextCacheService
        );
    }

    @Test
    void shouldUseCommonProjectNameAsTenderTitle() {
        AgentChatContext context = AgentChatContext.from(AgentChatReq.builder()
                .query("帮我看看这两份标书是否有围标风险")
                .build(), "session-1");

        AgentExecutionResult result = AgentExecutionResult.builder()
                .documentNames(List.of(
                        "城市视频云升级项目-商务标.docx",
                        "城市视频云升级项目-技术标.docx"
                ))
                .build();

        String title = ReflectionTestUtils.invokeMethod(
                agentSceneService,
                "generateTenderReviewTitle",
                context,
                result
        );

        assertEquals("城市视频云升级项目标书比对", title);
    }

    @Test
    void shouldFallbackToDocumentCountWhenNoCommonProjectName() {
        AgentChatContext context = AgentChatContext.from(AgentChatReq.builder()
                .query("分析这几份投标文件")
                .build(), "session-2");

        AgentExecutionResult result = AgentExecutionResult.builder()
                .documentNames(List.of(
                        "华东区域配送方案A.docx",
                        "西南区域配送方案B.docx",
                        "华北区域配送方案C.docx"
                ))
                .build();

        String title = ReflectionTestUtils.invokeMethod(
                agentSceneService,
                "generateTenderReviewTitle",
                context,
                result
        );

        assertEquals("华东区域配送方案A等3份标书审查", title);
    }

    @Test
    void shouldFallbackToQueryWhenDocumentNamesMissing() {
        AgentChatContext context = AgentChatContext.from(AgentChatReq.builder()
                .query("帮我看看这两份标书是否有围标风险")
                .build(), "session-3");

        AgentExecutionResult result = AgentExecutionResult.builder().build();

        String title = ReflectionTestUtils.invokeMethod(
                agentSceneService,
                "generateTenderReviewTitle",
                context,
                result
        );

        assertEquals("围串标风险审查", title);
    }
}
