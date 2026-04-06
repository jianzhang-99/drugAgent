package com.liang.drugagent.agent.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.liang.drugagent.shared.rag.entity.OssFile;
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
 *   <li>{@code userId} - 所属用户ID，数据隔离依据</li>
 *   <li>{@code summary} - 会话摘要，用于长对话压缩</li>
 *   <li>{@code lastScene} - 最近一次命中的场景</li>
 *   <li>{@code messageCount} - 消息总数</li>
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
     * 所属用户ID，用于数据隔离。
     */
    private String userId;

    /**
     * 会话摘要，用于长对话压缩。
     */
    private String summary;

    /**
     * 最近一次命中的场景。
     */
    private String lastScene;

    /**
     * 消息总数。
     */
    private Integer messageCount;

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
     * 最后一条消息时间。
     */
    private LocalDateTime lastMessageAt;

    /**
     * 关联的消息列表（非数据库字段，用于关联查询）。
     */
    @TableField(exist = false)
    private List<ChatMessage> messages;

    /**
     * 关联的附件列表（非数据库字段，用于关联查询）。
     */
    @TableField(exist = false)
    private List<OssFile> files;
}
