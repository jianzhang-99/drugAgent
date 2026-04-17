package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.facade.TenderReviewSceneService;
import com.liang.drugagent.shared.contextcache.DashScopeContextCacheService;
import com.liang.drugagent.shared.intent.IntentDetectionService;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentSceneServiceRouteTest {

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
    void shouldRouteToDefaultWhenTenderReviewSessionAsksWhatModel() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-model")
                .query("是什么模型")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-model");
        context.setSession(ChatSession.builder()
                .id("session-model")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.DEFAULT, decision.getScene());
        assertEquals("obvious-general", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }

    @Test
    void shouldReuseTenderReviewWhenFollowUpQuestionStillAboutTender() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-followup")
                .query("这两份标书还有哪些风险点")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-followup");
        context.setSession(ChatSession.builder()
                .id("session-followup")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
        assertEquals("session-context", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }

    /**
     * MT-03：标书审查会话后问"你还能做什么" → DEFAULT
     * 场景：session.lastScene = TENDER_REVIEW
     * 输入：query = "你还能做什么"
     * 预期：scene = DEFAULT, source = "obvious-general"（0层明显通用问题拦截）
     */
    @Test
    void shouldRouteToDefaultWhenAskingWhatElseCanDoInTenderReviewSession() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-mt03")
                .query("你还能做什么")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-mt03");
        context.setSession(ChatSession.builder()
                .id("session-mt03")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.DEFAULT, decision.getScene());
        assertEquals("obvious-general", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }

    /**
     * MT-04：标书审查会话中携带历史 fileIds 问通用问题 → DEFAULT（关键用例）
     * 场景：session.lastScene = TENDER_REVIEW，req.fileIds = ["fileId-1", "fileId-2"]（历史文件，非新上传）
     * 输入：query = "是什么模型"
     * 预期：scene = DEFAULT, source = "obvious-general"（0层明显通用问题拦截，fileIds不触发场景切换）
     * 说明：这个测试会在修复路由 Bug 后才能通过，是 P0 级别的回归测试
     */
    @Test
    void shouldRouteToDefaultWhenHistoricalFileIdsPresentWithGeneralQuery() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-mt04")
                .query("是什么模型")
                .fileIds(java.util.List.of("fileId-1", "fileId-2"))
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-mt04");
        context.setSession(ChatSession.builder()
                .id("session-mt04")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.DEFAULT, decision.getScene());
        assertEquals("obvious-general", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }

    /**
     * MT-05：携带历史 fileIds 追问标书相关问题 → TENDER_REVIEW
     * 场景：session.lastScene = TENDER_REVIEW，req.fileIds = ["fileId-1"]
     * 输入：query = "这两份标书还有什么风险"
     * 预期：scene = TENDER_REVIEW, source = "session-context"
     */
    @Test
    void shouldRouteToTenderReviewWhenFollowUpWithFileIdsAndTenderQuery() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-mt05")
                .query("这两份标书还有什么风险")
                .fileIds(java.util.List.of("fileId-1"))
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-mt05");
        context.setSession(ChatSession.builder()
                .id("session-mt05")
                .lastScene(SceneEnum.TENDER_REVIEW.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
        assertEquals("session-context", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }

    /**
     * MT-06：普通对话后问含标书关键词的问题 → TENDER_REVIEW（强规则）
     * 场景：session.lastScene = DEFAULT（或 null）
     * 输入：query = "帮我审查这份标书有没有围标风险"
     * 预期：scene = TENDER_REVIEW, source = "rule-strong"
     */
    @Test
    void shouldRouteToTenderReviewViaStrongRuleWhenQueryContainsTenderKeywords() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-mt06")
                .query("帮我审查这份标书有没有围标风险")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-mt06");
        context.setSession(ChatSession.builder()
                .id("session-mt06")
                .lastScene(SceneEnum.DEFAULT.name())
                .build());

        WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

        assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
        assertEquals("rule-strong", decision.getSource());
        verifyNoInteractions(intentDetectionService);
    }
}
