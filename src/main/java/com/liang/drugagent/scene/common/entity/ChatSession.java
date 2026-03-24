package com.liang.drugagent.scene.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话实体。
 *
 * <p>对应数据库表 {@code chat_session}，存储用户与AI的每一次对话会话。</p>
 *
 * <p>主要字段说明：
 * <ul>
 *   <li>{@code id} - 会话唯一标识，采用UUID自动生成</li>
 *   <li>{@code title} - 会话标题，默认值为"新对话"</li>
 *   <li>{@code scene} - 场景标识，用于区分不同业务场景</li>
 *   <li>{@code userId} - 所属用户ID，数据隔离依据</li>
 *   <li>{@code isDeleted} - 软删除标记，0-未删除，1-已删除</li>
 * </ul>
 *
 * <p>{@code messages} 字段为非数据库字段，用于关联查询时会话包含的消息列表。</p>
 *
 * @author liangjiajian
 * @see ChatMessage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    /**
     * 会话唯一标识，采用UUID自动生成。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 会话标题，默认值为"新对话"。
     */
    private String title;

    /**
     * 场景标识，用于区分不同业务场景（如 tender_review, compliance_review 等）。
     */
    private String scene;

    /**
     * 所属用户ID，用于数据隔离。
     */
    private String userId;

    /**
     * 软删除标记：0-未删除，1-已删除。
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 会话创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 会话最后更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 关联的消息列表（非数据库字段，用于关联查询）。
     */
    @TableField(exist = false)
    private List<ChatMessage> messages;
}
