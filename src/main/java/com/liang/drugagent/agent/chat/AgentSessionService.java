package com.liang.drugagent.agent.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.request.agent.CreateSessionReq;
import com.liang.drugagent.controller.domain.request.agent.SessionMessageReq;
import com.liang.drugagent.controller.domain.request.agent.UpdateSessionTitleReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.controller.domain.response.agent.SessionMessageResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 会话应用服务。
 *
 * <p>职责划分：
 * <ul>
 *   <li>会话 CRUD 与基础管理</li>
 *   <li>消息存储与查询</li>
 *   <li>加载会话上下文供 Agent 执行使用</li>
 *   <li>维护会话摘要与标题</li>
 * </ul>
 *
 * <p>不负责：场景判断、工作流执行、Tool 调用。
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    /**
     * 默认会话标题。
     */
    private static final String DEFAULT_SESSION_TITLE = "新对话";

    /**
     * 消息角色：用户。
     */
    private static final String ROLE_USER = "user";

    /**
     * 消息角色：助手。
     */
    private static final String ROLE_ASSISTANT = "assistant";

    /**
     * 消息角色：系统。
     */
    private static final String ROLE_SYSTEM = "system";

    private final ChatMemoryService chatMemoryService;
    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有会话列表（按最近更新时间倒序）。
     */
    public List<ChatSession> getAllSessions() {
        // MVP 单用户场景，直接返回所有未删除会话
        log.info("[AgentSessionService] 获取所有会话列表");
        return chatMemoryService.list()
                .stream()
                .filter(s -> s.getIsDeleted() == 0)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    /**
     * 根据会话ID获取会话详情（含消息列表）。
     */
    public ChatSession getSessionById(String sessionId) {
        log.info("[AgentSessionService] 获取会话详情，sessionId={}", sessionId);
        return chatMemoryService.getSessionWithMessages(sessionId);
    }

    /**
     * 创建新会话。
     */
    public ChatSession createSession(CreateSessionReq request) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : DEFAULT_SESSION_TITLE;
        String scene = request.getScene() != null ? request.getScene() : SceneEnum.UNKNOWN.name();
        // MVP 单用户场景，userId 使用默认值或空字符串
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        log.info("[AgentSessionService] 创建新会话，title={}，scene={}，userId={}", title, scene, userId);
        return chatMemoryService.createSession(title, scene, userId);
    }

    /**
     * 更新会话标题。
     */
    public void updateSessionTitle(String sessionId, UpdateSessionTitleReq request) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 更新会话标题失败，sessionId 为空");
            return;
        }
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            log.warn("[AgentSessionService] 更新会话标题失败，title 为空");
            return;
        }

        log.info("[AgentSessionService] 更新会话标题，sessionId={}，title={}", sessionId, request.getTitle());
        chatMemoryService.updateSessionTitle(sessionId, request.getTitle());
    }

    /**
     * 删除单个会话（软删除）。
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 删除会话失败，sessionId 为空");
            return;
        }
        log.info("[AgentSessionService] 删除会话，sessionId={}", sessionId);
        chatMemoryService.deleteSession(sessionId);
    }

    /**
     * 删除当前用户的全部会话（软删除）。
     * MVP 单用户场景，删除所有未删除会话。
     */
    public void deleteAllSessions() {
        log.info("[AgentSessionService] 删除所有会话");
        chatMemoryService.list()
                .stream()
                .filter(s -> s.getIsDeleted() == 0)
                .forEach(s -> chatMemoryService.deleteSession(s.getId()));
    }

    /**
     * 搜索会话（按标题模糊搜索）。
     */
    public List<ChatSession> searchSessions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllSessions();
        }
        log.info("[AgentSessionService] 搜索会话，keyword={}", keyword);
        // MVP 单用户场景，userId 使用默认值
        return chatMemoryService.searchSessions("default", keyword);
    }

    /**
     * 获取某个会话下的消息列表（按时间顺序）。
     */
    public List<ChatMessage> getMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 获取消息列表失败，sessionId 为空");
            return List.of();
        }
        log.info("[AgentSessionService] 获取消息列表，sessionId={}", sessionId);
        return chatMemoryService.getMessagesBySessionId(sessionId);
    }

    /**
     * Controller 层完整消息交互入口。
     * 保存用户消息 -> 调用 AgentChatService.chat -> 保存助手消息 -> 组装响应。
     */
    public SessionMessageResp addMessage(String sessionId, SessionMessageReq request) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 发送消息失败，sessionId 为空");
            return null;
        }

        log.info("[AgentSessionService] 处理用户消息，sessionId={}，content={}",
                sessionId, truncateContent(request.getContent()));

        // 1. 保存用户消息
        ChatMessage userMessage = saveUserMessage(sessionId, request.getContent(), serializeMetadata(request.getMetadata()));

        // 2. 调用 AgentChatService 执行对话
        AgentChatReq chatReq = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(request.getContent())
                .metadata(request.getMetadata())
                .build();

        AgentChatResp chatResp = agentChatService.chat(chatReq);

        // 3. 保存助手消息
        String assistantContent = chatResp != null ? chatResp.getAnswer() : "抱歉，发生了错误。";
        ChatMessage assistantMessage = saveAssistantMessage(
                sessionId,
                assistantContent,
                serializeResultMeta(chatResp),
                request.getType()
        );

        // 4. 更新会话摘要
        if (chatResp != null && chatResp.getSummary() != null) {
            updateSessionSummary(sessionId, chatResp.getSummary());
        }

        // 5. 组装响应
        return SessionMessageResp.builder()
                .message(assistantMessage)
                .userMessage(userMessage)
                .aiResponse(assistantContent)
                .traceId(chatResp != null ? chatResp.getTraceId() : null)
                .scene(chatResp != null ? chatResp.getScene() : null)
                .build();
    }

    /**
     * 获取或创建会话。
     * 若 sessionId 有效则直接返回；否则创建新会话。
     */
    public ChatSession getOrCreateSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession existing = chatMemoryService.getById(sessionId);
            if (existing != null && existing.getIsDeleted() == 0) {
                log.info("[AgentSessionService] 会话已存在，直接返回，sessionId={}", sessionId);
                return existing;
            }
        }
        // 创建新会话
        log.info("[AgentSessionService] 会话不存在或已删除，创建新会话");
        CreateSessionReq request = CreateSessionReq.builder()
                .title(DEFAULT_SESSION_TITLE)
                .scene(SceneEnum.UNKNOWN.name())
                .build();
        return createSession(request);
    }

    /**
     * 加载会话运行时上下文。
     * 包含 session、最近消息、摘要、最近场景等信息。
     */
    public AgentSessionContext loadSessionContext(String sessionId) {
        log.info("[AgentSessionService] 加载会话上下文，sessionId={}", sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            return AgentSessionContext.empty(sessionId);
        }

        ChatSession session = chatMemoryService.getSessionWithMessages(sessionId);
        if (session == null) {
            log.warn("[AgentSessionService] 会话不存在，sessionId={}", sessionId);
            return AgentSessionContext.empty(sessionId);
        }

        // 获取最近消息（限制条数）
        List<ChatMessage> recentMessages = chatMemoryService.getMessagesBySessionId(sessionId);
        int maxMessages = 20;
        if (recentMessages.size() > maxMessages) {
            recentMessages = recentMessages.subList(recentMessages.size() - maxMessages, recentMessages.size());
        }

        // 获取最后场景
        SceneEnum lastScene = SceneEnum.fromHint(session.getScene());

        return new AgentSessionContext(
                sessionId,
                session,
                recentMessages,
                session.getTitle(),
                lastScene
        );
    }

    /**
     * 保存用户消息。
     */
    public ChatMessage saveUserMessage(String sessionId, String content, String metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 保存用户消息失败，sessionId 为空");
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentSessionService] 保存用户消息失败，content 为空");
            return null;
        }
        log.info("[AgentSessionService] 保存用户消息，sessionId={}，content={}",
                sessionId, truncateContent(content));
        return chatMemoryService.addMessage(sessionId, ROLE_USER, content, metadata);
    }

    /**
     * 保存助手消息。
     */
    public ChatMessage saveAssistantMessage(String sessionId, String content, String metadata, String type) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 保存助手消息失败，sessionId 为空");
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentSessionService] 保存助手消息失败，content 为空");
            return null;
        }
        log.info("[AgentSessionService] 保存助手消息，sessionId={}，type={}",
                sessionId, type != null ? type : "text");
        return chatMemoryService.addMessage(sessionId, ROLE_ASSISTANT, content, metadata, type);
    }

    /**
     * 保存系统消息。
     */
    public void saveSystemMessage(String sessionId, String content, String metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 保存系统消息失败，sessionId 为空");
            return;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentSessionService] 保存系统消息失败，content 为空");
            return;
        }
        log.info("[AgentSessionService] 保存系统消息，sessionId={}", sessionId);
        chatMemoryService.addMessage(sessionId, ROLE_SYSTEM, content, metadata);
    }

    /**
     * 更新会话摘要。
     */
    public void updateSessionSummary(String sessionId, String summary) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 更新会话摘要失败，sessionId 为空");
            return;
        }
        if (summary == null || summary.isBlank()) {
            return;
        }
        log.info("[AgentSessionService] 更新会话摘要，sessionId={}，summary={}",
                sessionId, truncateContent(summary));
        // 摘要存储在 title 字段或专门的 summary 字段，这里暂用 title 字段存储摘要
        // 实际生产中应扩展 ChatSession 表添加 summary 字段
        chatMemoryService.updateSessionTitle(sessionId, truncateContent(summary, 100));
    }

    /**
     * 如有必要则更新会话标题。
     * 若当前仍是默认标题，则根据首轮问题自动生成标题。
     */
    public void updateSessionTitleIfNeeded(String sessionId, String query) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (query == null || query.isBlank()) {
            return;
        }

        ChatSession session = chatMemoryService.getById(sessionId);
        if (session == null) {
            return;
        }

        // 如果标题仍是默认标题，则根据首轮问题生成标题
        if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) || session.getTitle() == null) {
            String newTitle = truncateContent(query, 30);
            log.info("[AgentSessionService] 自动生成会话标题，sessionId={}，title={}", sessionId, newTitle);
            chatMemoryService.updateSessionTitle(sessionId, newTitle);
        }
    }

    /**
     * 记录一次执行链路的元信息。
     */
    public void appendExecutionTrace(String sessionId, String traceId, SceneEnum scene, String resultMeta) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        log.info("[AgentSessionService] 记录执行链路，sessionId={}，traceId={}，scene={}",
                sessionId, traceId, scene != null ? scene.name() : null);

        String traceContent = String.format("【执行链路】traceId=%s，scene=%s，结果=%s",
                traceId,
                scene != null ? scene.name() : "unknown",
                resultMeta != null ? truncateContent(resultMeta, 100) : "N/A");

        saveSystemMessage(sessionId, traceContent, null);
    }

    /**
     * Agent 会话运行时上下文。
     */
    public record AgentSessionContext(
            String sessionId,
            ChatSession session,
            List<ChatMessage> recentMessages,
            String summary,
            SceneEnum lastScene
    ) {
        public static AgentSessionContext empty(String sessionId) {
            return new AgentSessionContext(sessionId, null, List.of(), null, null);
        }
    }

    /**
     * 将元数据对象序列化为 JSON 字符串。
     */
    private String serializeMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("[AgentSessionService] 序列化元数据失败", e);
            return null;
        }
    }

    /**
     * 将 AgentChatResp 结果序列化为元数据字符串。
     */
    private String serializeResultMeta(AgentChatResp resp) {
        if (resp == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException e) {
            log.warn("[AgentSessionService] 序列化响应结果失败", e);
            return null;
        }
    }

    /**
     * 截断内容用于日志展示。
     */
    private String truncateContent(String content) {
        return truncateContent(content, 100);
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
