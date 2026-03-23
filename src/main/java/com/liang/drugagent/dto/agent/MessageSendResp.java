package com.liang.drugagent.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MessageSendResp", description = "发送消息响应")
public class MessageSendResp {

    @Schema(description = "用户消息")
    private String userMessage;

    @Schema(description = "AI响应")
    private String aiResponse;

    @Schema(description = "追踪ID")
    private String traceId;

    @Schema(description = "场景")
    private String scene;

    @Schema(description = "路由来源")
    private String routeSource;

    @Schema(description = "路由原因")
    private String routeReason;

    @Schema(description = "置信度")
    private Double confidence;
}
