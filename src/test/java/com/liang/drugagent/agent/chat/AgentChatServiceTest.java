package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentChatService 集成测试。
 *
 * <p>测试真实的模型调用链路：
 * AgentChatService.chat() -> AgentSceneService.decideAndExecute() -> LlmService.chat()
 */
@SpringBootTest(classes = com.liang.drugagent.app.DrugAgentApplication.class)
@ActiveProfiles("test")
class AgentChatServiceTest {

    @Autowired
    private AgentChatService agentChatService;

    @SpyBean
    private AgentSessionService agentSessionService;

    @SpyBean
    private ChatMemoryService chatMemoryService;

    @Test
    void chat_shouldReturnResponse_whenNormalConversation() {
        // 准备测试数据
        String sessionId = "test-session-integration-123";
        String query = "你好，请介绍一下药品监管的基本内容";

        ChatSession mockSession = ChatSession.builder()
                .id(sessionId)
                .title("测试会话")
                .build();

        AgentChatReq request = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query)
                .build();

        // Mock 会话服务，只打桩不拦截实际逻辑
        doReturn(new AgentSessionService.AgentSessionContext(sessionId, mockSession, List.of(), "测试会话"))
                .when(agentSessionService).loadSessionContext(any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveUserMessage(any(), any(), any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveAssistantMessage(any(), any(), any(), any());
        doReturn(mockSession).when(agentSessionService).getOrCreateSession(any());

        // 执行测试 - 实际调用模型
        AgentChatResp response = agentChatService.chat(request);

        // 验证
        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
        assertNotNull(response.getAnswer());
        System.out.println("模型返回: " + response.getAnswer());

        // 验证会话服务被调用
        verify(agentSessionService, atLeastOnce()).loadSessionContext(any());
    }

    @Test
    void chat_shouldReturnResponse_whenQuestionAboutDrugReview() {
        // 准备测试数据 - 问一个药品监管相关的问题
        String sessionId = "test-session-integration-456";
        String query = "药品上市后有哪些监管措施？";

        AgentChatReq request = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query)
                .build();

        // Mock 会话服务
        ChatSession mockSession = ChatSession.builder()
                .id(sessionId)
                .title("药品监管会话")
                .build();
        doReturn(new AgentSessionService.AgentSessionContext(sessionId, mockSession, List.of(), "药品监管会话"))
                .when(agentSessionService).loadSessionContext(any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveUserMessage(any(), any(), any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveAssistantMessage(any(), any(), any(), any());
        doReturn(mockSession).when(agentSessionService).getOrCreateSession(any());

        // 执行测试 - 实际调用模型
        AgentChatResp response = agentChatService.chat(request);

        // 验证
        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isEmpty());

        System.out.println("问题: " + query);
        System.out.println("模型返回: " + response.getAnswer());

        // 验证调用了会话服务
        verify(agentSessionService, atLeastOnce()).loadSessionContext(any());
    }

    @Test
    void chat_shouldHandleError_whenEmptyQuery() {
        // 准备测试数据 - 空查询
        String sessionId = "test-session-integration-789";
        String query = "";

        AgentChatReq request = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query)
                .build();

        // Mock 会话服务
        ChatSession mockSession = ChatSession.builder()
                .id(sessionId)
                .title("空查询测试")
                .build();
        doReturn(new AgentSessionService.AgentSessionContext(sessionId, mockSession, List.of(), "空查询测试"))
                .when(agentSessionService).loadSessionContext(any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveUserMessage(any(), any(), any());
        doReturn(mock(ChatMessage.class)).when(agentSessionService).saveAssistantMessage(any(), any(), any(), any());
        doReturn(mockSession).when(agentSessionService).getOrCreateSession(any());

        // 执行测试
        AgentChatResp response = agentChatService.chat(request);

        // 验证
        assertNotNull(response);
        // 空查询时系统仍会尝试处理
        assertNotNull(response.getAnswer());
    }
}
