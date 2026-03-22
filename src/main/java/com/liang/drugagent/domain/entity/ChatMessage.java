package com.liang.drugagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;
    private String role;
    private String content;
    private String metadata;  // JSON字符串存储扩展信息

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
