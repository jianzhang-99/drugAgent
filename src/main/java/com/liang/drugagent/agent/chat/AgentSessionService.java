package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.request.agent.CreateSessionReq;
import com.liang.drugagent.controller.domain.request.agent.SessionMessageReq;
import com.liang.drugagent.controller.domain.request.agent.UpdateSessionTitleReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.controller.domain.response.agent.SessionMessageResp;
import com.liang.drugagent.scene.common.MessageTypeEnum;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 会话应用服务。
 */
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private static final String DEFAULT_USER_ID = "default_user";
    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final String DEFAULT_SCENE = "general";

    private final AgentChatService agentChatService;
    private final ChatMemoryService chatMemoryService;

    public List<ChatSession> getAllSessions() {
        return chatMemoryService.getSessionsByUserId(DEFAULT_USER_ID);
    }

    public ChatSession getSessionById(String sessionId) {
        return chatMemoryService.getSessionWithMessages(sessionId);
    }

    public ChatSession createSession(CreateSessionReq request) {
        String title = request != null && request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle()
                : DEFAULT_SESSION_TITLE;
        String scene = request != null && request.getScene() != null && !request.getScene().isBlank()
                ? request.getScene()
                : DEFAULT_SCENE;
        return chatMemoryService.createSession(title, scene, DEFAULT_USER_ID);
    }

    public void updateSessionTitle(String sessionId, UpdateSessionTitleReq request) {
        String title = request == null ? null : request.getTitle();
        chatMemoryService.updateSessionTitle(sessionId, title);
    }

    public void deleteSession(String sessionId) {
        chatMemoryService.deleteSession(sessionId);
    }

    public void deleteAllSessions() {
        chatMemoryService.deleteAllSessions(DEFAULT_USER_ID);
    }

    public List<ChatSession> searchSessions(String keyword) {
        return chatMemoryService.searchSessions(DEFAULT_USER_ID, keyword);
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return chatMemoryService.getMessagesBySessionId(sessionId);
    }

    public SessionMessageResp addMessage(String sessionId, SessionMessageReq request) {
        String role = request.getRole() == null || request.getRole().isBlank() ? "user" : request.getRole();
        ChatMessage userMessage = chatMemoryService.addMessage(sessionId, role, request.getContent(), null, null);

        if (!"user".equals(role)) {
            return SessionMessageResp.builder()
                    .message(userMessage)
                    .userMessage(userMessage)
                    .aiResponse("")
                    .traceId(null)
                    .scene(null)
                    .build();
        }

        DrugAgentResp aiResponse = agentChatService.chat(AgentChatReq.builder()
                .sessionId(sessionId)
                .query(request.getContent())
                .build());

        String messageType = determineMessageType(aiResponse);
        String aiContent = aiResponse.getAnswer() != null ? aiResponse.getAnswer() : aiResponse.getSummary();
        ChatMessage assistantMessage = chatMemoryService.addMessage(sessionId, "assistant", aiContent, null, messageType);

        return SessionMessageResp.builder()
                .message(assistantMessage)
                .userMessage(userMessage)
                .aiResponse(aiResponse.getAnswer())
                .traceId(aiResponse.getTraceId())
                .scene(aiResponse.getScene())
                .build();
    }

    private String determineMessageType(DrugAgentResp resp) {
        if (resp == null) {
            return MessageTypeEnum.ASSISTANT_TEXT.getCode();
        }
        if (resp.isRequiresClarification()) {
            return MessageTypeEnum.ASSISTANT_CLARIFY.getCode();
        }
        if (resp.getReport() != null) {
            return MessageTypeEnum.ASSISTANT_RESULT_CARD.getCode();
        }
        return MessageTypeEnum.ASSISTANT_TEXT.getCode();
    }
}
