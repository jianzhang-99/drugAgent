package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionReq {

    /** 会话标题 */
    private String title;

    /** 场景标识 */
    private String scene;
}
