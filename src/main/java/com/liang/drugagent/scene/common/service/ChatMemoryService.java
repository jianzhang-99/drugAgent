package com.liang.drugagent.scene.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.scene.common.mapper.ChatMessageMapper;
import com.liang.drugagent.scene.common.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天记忆服务。
 *
 * <p>提供会话管理和消息存储能力，支持：
 * <ul>
 *   <li>会话的创建、查询、更新、软删除</li>
 *   <li>消息的添加和查询</li>
 *   <li>基于标题的会话搜索</li>
 * </ul>
 *
 * <p>所有数据均基于用户ID隔离，支持软删除机制。</p>
 *
 * @author liangjiajian
 * @see ChatSession
 * @see ChatMessage
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    /**
     * 消息Mapper，用于消息的数据库操作。
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 创建新会话。
     *
     * @param title 会话标题
     * @param scene 场景标识
     * @param userId 用户ID
     * @return 创建的会话实例
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
     * 获取用户所有未删除会话（按更新时间倒序）。
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<ChatSession> getSessionsByUserId(String userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .orderByDesc(ChatSession::getUpdatedAt);
        return this.list(wrapper);
    }

    /**
     * 获取会话详情（含消息列表）。
     *
     * @param sessionId 会话ID
     * @return 会话实例，包含消息列表
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
     * 更新会话标题。
     *
     * @param sessionId 会话ID
     * @param title 新标题
     * @return 更新是否成功
     */
    public boolean updateSessionTitle(String sessionId, String title) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        return this.updateById(session);
    }

    /**
     * 软删除会话（将isDeleted标记设为1）。
     *
     * @param sessionId 会话ID
     * @return 删除是否成功
     */
    public boolean deleteSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setIsDeleted(1);
        return this.updateById(session);
    }

    /**
     * 软删除用户的所有会话。
     *
     * @param userId 用户ID
     */
    public void deleteAllSessions(String userId) {
        ChatSession session = new ChatSession();
        session.setIsDeleted(1);
        UpdateWrapper<ChatSession> wrapper = new UpdateWrapper<>();
        wrapper.eq("user_id", userId);
        this.update(session, wrapper);
    }

    /**
     * 搜索会话（按标题模糊搜索）。
     *
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @return 匹配的会话列表
     */
    public List<ChatSession> searchSessions(String userId, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0)
               .like(ChatSession::getTitle, keyword)
               .orderByDesc(ChatSession::getUpdatedAt);
        return this.list(wrapper);
    }

    /**
     * 添加消息到会话。
     *
     * @param sessionId 会话ID
     * @param role 消息角色（user/assistant/system）
     * @param content 消息内容
     * @param metadata 扩展信息（JSON格式，可为空）
     * @return 创建的消息实例
     */
    public ChatMessage addMessage(String sessionId, String role, String content, String metadata) {
        return addMessage(sessionId, role, content, metadata, null);
    }

    /**
     * 添加消息到会话（带消息类型）。
     *
     * @param sessionId 会话ID
     * @param role 消息角色（user/assistant/system）
     * @param content 消息内容
     * @param metadata 扩展信息（JSON格式，可为空）
     * @param type 消息类型（如 assistant_text, assistant_clarify, assistant_result_card）
     * @return 创建的消息实例
     */
    public ChatMessage addMessage(String sessionId, String role, String content, String metadata, String type) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .metadata(metadata)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 获取会话的所有消息（按创建时间升序）。
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }
}
