package com.liang.drugagent.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.domain.entity.ChatMessage;
import com.liang.drugagent.domain.entity.ChatSession;
import com.liang.drugagent.mapper.ChatMessageMapper;
import com.liang.drugagent.mapper.ChatSessionMapper;
import com.liang.drugagent.repository.AgentSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent会话仓储实现。
 *
 * @author liangjiajian
 */
@Repository
public class AgentSessionRepositoryImpl implements AgentSessionRepository {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    public AgentSessionRepositoryImpl(ChatSessionMapper chatSessionMapper,
                                     ChatMessageMapper chatMessageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public ChatSession create(ChatSession session) {
        session.setIsDeleted(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    @Override
    public Optional<ChatSession> findById(String id) {
        ChatSession session = chatSessionMapper.selectById(id);
        return Optional.ofNullable(session);
    }

    @Override
    public List<ChatSession> findByUserId(String userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .orderByDesc(ChatSession::getUpdatedAt);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public ChatSession findWithMessages(String sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessage::getSessionId, sessionId)
                   .orderByAsc(ChatMessage::getCreatedAt);
            session.setMessages(chatMessageMapper.selectList(wrapper));
        }
        return session;
    }

    @Override
    public boolean updateTitle(String sessionId, String title) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionMapper.updateById(session) > 0;
    }

    @Override
    public boolean delete(String sessionId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setIsDeleted(1);
        return chatSessionMapper.updateById(session) > 0;
    }

    @Override
    public List<ChatSession> search(String userId, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .like(ChatSession::getTitle, keyword)
               .orderByDesc(ChatSession::getUpdatedAt);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public ChatMessage addMessage(ChatMessage message) {
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessage> getMessages(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }
}
