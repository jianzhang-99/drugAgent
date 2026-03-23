package com.liang.drugagent.repository;

import com.liang.drugagent.domain.entity.ChatMessage;
import com.liang.drugagent.domain.entity.ChatSession;

import java.util.List;
import java.util.Optional;

/**
 * Agent会话仓储接口。
 *
 * @author liangjiajian
 */
public interface AgentSessionRepository {

    /**
     * 创建会话。
     */
    ChatSession create(ChatSession session);

    /**
     * 根据ID获取会话。
     */
    Optional<ChatSession> findById(String id);

    /**
     * 根据用户ID获取所有会话。
     */
    List<ChatSession> findByUserId(String userId);

    /**
     * 获取会话详情（含消息）。
     */
    ChatSession findWithMessages(String sessionId);

    /**
     * 更新会话标题。
     */
    boolean updateTitle(String sessionId, String title);

    /**
     * 软删除会话。
     */
    boolean delete(String sessionId);

    /**
     * 搜索会话。
     */
    List<ChatSession> search(String userId, String keyword);

    /**
     * 添加消息。
     */
    ChatMessage addMessage(ChatMessage message);

    /**
     * 获取会话消息。
     */
    List<ChatMessage> getMessages(String sessionId);
}
