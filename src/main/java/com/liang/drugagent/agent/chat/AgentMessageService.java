package com.liang.drugagent.agent.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.MessageRole;
import com.liang.drugagent.agent.common.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 聊天消息服务。
 *
 * <p>职责：消息的存储与查询
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMessageService extends ServiceImpl<ChatMessageMapper, ChatMessage> {


    /**
     * 添加消息到会话。
     */
    public ChatMessage addMessage(String sessionId, String role, String content, String metadata) {
        return addMessage(sessionId, role, content, metadata, null);
    }

    /**
     * 添加消息到会话（带消息类型）。
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
        this.baseMapper.insert(message);
        return message;
    }

    /**
     * 获取会话的所有消息（按创建时间升序）。
     */
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return this.baseMapper.selectList(wrapper);
    }

    /**
     * 获取会话最近 N 条消息（按创建时间升序返回，取最后 N 条）。
     * <p>替代"查全量再截断"的低效写法。
     */
    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + limit);
        List<ChatMessage> messages = this.baseMapper.selectList(wrapper);
        // 反转回升序
        return messages != null ? messages.reversed() : List.of();
    }

    /**
     * 保存用户消息。
     */
    public ChatMessage saveUserMessage(String sessionId, String content, String metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentMessageService] 保存用户消息失败，sessionId 为空");
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentMessageService] 保存用户消息失败，content 为空");
            return null;
        }
        log.info("[AgentMessageService] 保存用户消息，sessionId={}", sessionId);
        return addMessage(sessionId, MessageRole.USER.getValue(), content, metadata);
    }

    /**
     * 保存助手消息。
     */
    public ChatMessage saveAssistantMessage(String sessionId, String content, String metadata, String type) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentMessageService] 保存助手消息失败，sessionId 为空");
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentMessageService] 保存助手消息失败，content 为空");
            return null;
        }
        log.info("[AgentMessageService] 保存助手消息，sessionId={}，type={}", sessionId, type != null ? type : "text");
        return addMessage(sessionId, MessageRole.ASSISTANT.getValue(), content, metadata, type);
    }

    /**
     * 保存系统消息。
     */
    public ChatMessage saveSystemMessage(String sessionId, String content, String metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[AgentMessageService] 保存系统消息失败，sessionId 为空");
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[AgentMessageService] 保存系统消息失败，content 为空");
            return null;
        }
        log.info("[AgentMessageService] 保存系统消息，sessionId={}", sessionId);
        return addMessage(sessionId, MessageRole.SYSTEM.getValue(), content, metadata);
    }
}
