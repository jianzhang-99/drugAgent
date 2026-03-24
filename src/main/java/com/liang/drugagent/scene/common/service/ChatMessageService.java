package com.liang.drugagent.scene.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.drugagent.thirdparty.db.ChatMessageMapper;
import com.liang.drugagent.thirdparty.db.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMessageService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    private final ChatMessageMapper chatMessageMapper;

    /**
     * 添加消息到会话
     */
    public ChatMessage addMessage(String sessionId, String role, String content, String metadata) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 获取会话的所有消息
     */
    public java.util.List<ChatMessage> getMessagesBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }
}
