package com.liang.drugagent.agent.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体。
 *
 * <p>对应数据库表 {@code chat_message}，存储会话中的每一条消息。</p>
 *
 * <p>消息角色说明：
 * <ul>
 *   <li>{@code user} - 用户发送的消息</li>
 *   <li>{@code assistant} - AI助手回复的消息</li>
 *   <li>{@code system} - 系统消息</li>
 * </ul>
 *
 * <p>消息类型说明（type字段）：
 * <ul>
 *   <li>{@code assistant_text} - 普通文本回复</li>
 *   <li>{@code assistant_clarify} - 需要用户澄清的问题</li>
 *   <li>{@code assistant_result_card} - 结构化结果卡片（如审查报告）</li>
 * </ul>
 *
 * <p>{@code metadata} 字段以JSON字符串形式存储扩展信息，如上传文件列表等。</p>
 *
 * @author liangjiajian
 * @see ChatSession
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    /**
     * 消息唯一标识，采用UUID自动生成。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属会话ID。
     */
    private String sessionId;

    /**
     * 消息角色：user-用户、assistant-助手、system-系统。
     */
    private String role;

    /**
     * 本轮消息对应场景。
     */
    private String scene;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 扩展信息（JSON字符串），用于存储上传文件列表等附加数据。
     */
    private String metadata;

    /**
     * 消息类型：
     * <ul>
     *   <li>assistant_text - 普通文本回复</li>
     *   <li>assistant_clarify - 需要澄清的问题</li>
     *   <li>assistant_result_card - 结构化结果卡片</li>
     * </ul>
     */
    private String type;

    /**
     * 消息创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
