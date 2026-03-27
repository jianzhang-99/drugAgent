package com.liang.drugagent.agent.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.agent.common.mapper.ChatSessionMapper;
import com.liang.drugagent.controller.domain.request.agent.CreateSessionReq;
import com.liang.drugagent.controller.domain.request.agent.UpdateSessionTitleReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 会话服务。
 *
 * <p>职责：
 * <ul>
 *   <li>会话 CRUD 与基础管理</li>
 *   <li>加载会话上下文供 Agent 执行使用</li>
 *   <li>维护会话摘要与标题</li>
 * </ul>
 *
 * <p>消息操作委托给 AgentChatMessageService
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    /**
     * 默认会话标题。
     */
    private static final String DEFAULT_SESSION_TITLE = "新对话";

    private final AgentChatMessageService agentChatMessageService;

    // ==================== 会话 CRUD ====================

    /**
     * 获取所有会话列表（按最近更新时间倒序）。
     */
    public List<ChatSession> getAllSessions() {
        log.info("[AgentSessionService] 获取所有会话列表");
        return this.list()
                .stream()
                .filter(s -> s.getIsDeleted() == 0)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    /**
     * 根据会话ID获取会话详情。
     */
    public ChatSession getSessionById(String sessionId) {
        log.info("[AgentSessionService] 获取会话详情，sessionId={}", sessionId);
        return getSessionWithMessages(sessionId);
    }

    /**
     * 创建新会话。
     */
    public ChatSession createSession(CreateSessionReq request) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : DEFAULT_SESSION_TITLE;
        String scene = request.getScene() != null ? request.getScene() : "UNKNOWN";
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        log.info("[AgentSessionService] 创建新会话，title={}，scene={}，userId={}", title, scene, userId);
        return createSession(title, scene, userId);
    }

    /**
     * 创建新会话。
     */
    public ChatSession createSession(String title, String scene, String userId) {
        ChatSession session = ChatSession.builder()
                .title(title)
                .userId(userId)
                .isDeleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        this.save(session);
        return session;
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
        updateSessionTitle(sessionId, request.getTitle());
    }

    /**
     * 更新会话标题。
     */
    public boolean updateSessionTitle(String sessionId, String title) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        return this.updateById(session);
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
        softDeleteSession(sessionId);
    }

    /**
     * 软删除会话。
     */
    private boolean softDeleteSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setIsDeleted(1);
        return this.updateById(session);
    }

    /**
     * 删除当前用户的全部会话（软删除）。
     */
    public void deleteAllSessions() {
        log.info("[AgentSessionService] 删除所有会话");
        this.list()
                .stream()
                .filter(s -> s.getIsDeleted() == 0)
                .forEach(s -> softDeleteSession(s.getId()));
    }

    /**
     * 搜索会话（按标题模糊搜索）。
     */
    public List<ChatSession> searchSessions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllSessions();
        }
        log.info("[AgentSessionService] 搜索会话，keyword={}", keyword);
        return searchSessions("default", keyword);
    }

    /**
     * 搜索会话。
     */
    public List<ChatSession> searchSessions(String userId, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .like(ChatSession::getTitle, keyword)
               .orderByDesc(ChatSession::getUpdatedAt);
        return this.list(wrapper);
    }

    // ==================== 会话上下文 ====================

    /**
     * 获取或创建会话。
     */
    public ChatSession getOrCreateSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession existing = this.getById(sessionId);
            if (existing != null && existing.getIsDeleted() == 0) {
                log.info("[AgentSessionService] 会话已存在，直接返回，sessionId={}", sessionId);
                return existing;
            }
        }
        log.info("[AgentSessionService] 会话不存在或已删除，创建新会话");
        return createSession(DEFAULT_SESSION_TITLE, "UNKNOWN", "default");
    }

    /**
     * 加载会话运行时上下文。
     */
    public AgentSessionContext loadSessionContext(String sessionId) {
        log.info("[AgentSessionService] 加载会话上下文，sessionId={}", sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            return AgentSessionContext.empty(sessionId);
        }

        ChatSession session = getSessionWithMessages(sessionId);
        if (session == null) {
            log.warn("[AgentSessionService] 会话不存在，sessionId={}", sessionId);
            return AgentSessionContext.empty(sessionId);
        }

        // 获取最近消息（限制条数）
        List<ChatMessage> recentMessages = agentChatMessageService.getMessagesBySessionId(sessionId);
        int maxMessages = 20;
        if (recentMessages.size() > maxMessages) {
            recentMessages = recentMessages.subList(recentMessages.size() - maxMessages, recentMessages.size());
        }

        return new AgentSessionContext(
                sessionId,
                session,
                recentMessages,
                session.getTitle()
        );
    }

    /**
     * 获取会话详情（含消息列表）。
     */
    public ChatSession getSessionWithMessages(String sessionId) {
        ChatSession session = this.getById(sessionId);
        if (session != null) {
            List<ChatMessage> messages = agentChatMessageService.getMessagesBySessionId(sessionId);
            session.setMessages(messages);
        }
        return session;
    }

    // ==================== 消息保存（委托） ====================

    /**
     * 保存用户消息。
     */
    public ChatMessage saveUserMessage(String sessionId, String content, String metadata) {
        return agentChatMessageService.saveUserMessage(sessionId, content, metadata);
    }

    /**
     * 保存助手消息。
     */
    public ChatMessage saveAssistantMessage(String sessionId, String content, String metadata, String type) {
        return agentChatMessageService.saveAssistantMessage(sessionId, content, metadata, type);
    }

    /**
     * 保存系统消息。
     */
    public void saveSystemMessage(String sessionId, String content, String metadata) {
        agentChatMessageService.saveSystemMessage(sessionId, content, metadata);
    }

    // ==================== 摘要与标题 ====================

    /**
     * 更新会话摘要。
     * <p>只更新 summary 字段，不更新 title。
     */
    public void updateSessionSummary(String sessionId, String summary) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentSessionService] 更新会话摘要失败，sessionId 为空");
            return;
        }
        if (summary == null || summary.isBlank()) {
            return;
        }
        log.info("[AgentSessionService] 更新会话摘要，sessionId={}", sessionId);
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setSummary(truncateContent(summary, 500));
        session.setUpdatedAt(LocalDateTime.now());
        this.updateById(session);
    }

    /**
     * 更新会话活跃状态。
     * <p>更新 lastScene、lastMessageAt、updatedAt。
     */
    public void touchSession(String sessionId, String scene) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setLastScene(scene);
        session.setLastMessageAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        this.updateById(session);
    }

    /**
     * 增加会话消息计数。
     */
    public void increaseMessageCount(String sessionId, int delta) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ChatSession existing = this.getById(sessionId);
        if (existing == null) {
            return;
        }
        int currentCount = existing.getMessageCount() != null ? existing.getMessageCount() : 0;
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setMessageCount(currentCount + delta);
        session.setUpdatedAt(LocalDateTime.now());
        this.updateById(session);
    }

    /**
     * 如有必要则更新会话标题。
     */
    public void updateSessionTitleIfNeeded(String sessionId, String query) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (query == null || query.isBlank()) {
            return;
        }

        ChatSession session = this.getById(sessionId);
        if (session == null) {
            return;
        }

        if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) || session.getTitle() == null) {
            String newTitle = truncateContent(query, 30);
            log.info("[AgentSessionService] 自动生成会话标题，sessionId={}，title={}", sessionId, newTitle);
            updateSessionTitle(sessionId, newTitle);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录一次执行链路的元信息。
     */
    public void appendExecutionTrace(String sessionId, String traceId, String scene, String resultMeta) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        log.info("[AgentSessionService] 记录执行链路，sessionId={}，traceId={}，scene={}",
                sessionId, traceId, scene);

        String traceContent = String.format("【执行链路】traceId=%s，scene=%s，结果=%s",
                traceId,
                scene != null ? scene : "unknown",
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
            String summary
    ) {
        public static AgentSessionContext empty(String sessionId) {
            return new AgentSessionContext(sessionId, null, List.of(), null);
        }
    }

    /**
     * 截断内容用于日志展示。
     */
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
