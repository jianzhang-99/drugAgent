package com.liang.drugagent.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话创建请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SessionCreateReq", description = "创建会话请求")
public class SessionCreateReq {

    @Schema(description = "会话标题", example = "新会话")
    private String title;

    @Schema(description = "场景类型", example = "general")
    private String scene;
}
