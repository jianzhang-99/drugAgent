package com.liang.drugagent.thirdparty.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String scene;
    private String userId;

    @TableLogic  // 软删除注解
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)  // 非数据库字段
    private List<ChatMessage> messages;
}
