package com.liang.drugagent.controller.domain.response.agent;

import com.liang.drugagent.agent.common.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话发消息响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageResp {

    /** 消息对象 */
    private ChatMessage message;

    /** 用户消息对象 */
    private ChatMessage userMessage;

    /** AI回复内容 */
    private String aiResponse;

    /** 链路追踪ID */
    private String traceId;

    /** 场景标识 */
    private String scene;
}
