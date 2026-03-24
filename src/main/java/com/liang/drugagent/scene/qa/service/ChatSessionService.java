package com.liang.drugagent.scene.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.drugagent.thirdparty.db.ChatMessageMapper;
import com.liang.drugagent.thirdparty.db.ChatSessionMapper;
import com.liang.drugagent.thirdparty.db.entity.ChatMessage;
import com.liang.drugagent.thirdparty.db.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    private final ChatMessageMapper chatMessageMapper;

    /**
     * 创建新会话
     */
    public ChatSession createSession(String title, String scene, String userId) {
        ChatSession session = ChatSession.builder()
                .title(title)
                .scene(scene)
                .userId(userId)
                .isDeleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        this.save(session);
        return session;
    }

    /**
     * 获取用户所有未删除会话（按更新时间倒序）
     */
    public List<ChatSession> getSessionsByUserId(String userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .orderByDesc(ChatSession::getUpdatedAt);
        return this.list(wrapper);
    }

    /**
     * 获取会话详情（含消息）
     */
    public ChatSession getSessionWithMessages(String sessionId) {
        ChatSession session = this.getById(sessionId);
        if (session != null) {
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessage::getSessionId, sessionId)
                   .orderByAsc(ChatMessage::getCreatedAt);
            session.setMessages(chatMessageMapper.selectList(wrapper));
        }
        return session;
    }

    /**
     * 更新会话标题
     */
    public boolean updateSessionTitle(String sessionId, String title) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        return this.updateById(session);
    }

    /**
     * 软删除会话
     */
    public boolean deleteSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setIsDeleted(1);
        return this.updateById(session);
    }

    /**
     * 搜索会话（按标题模糊搜索）
     */
    public List<ChatSession> searchSessions(String userId, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .like(ChatSession::getTitle, keyword)
               .orderByDesc(ChatSession::getUpdatedAt);
        return this.list(wrapper);
    }
}
