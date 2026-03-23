package com.liang.drugagent.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MessageSendReq", description = "发送消息请求")
public class MessageSendReq {

    @Schema(description = "角色", example = "user")
    private String role;

    @Schema(description = "消息内容", example = "你好")
    private String content;

    @Schema(description = "元数据JSON")
    private String metadata;
}
