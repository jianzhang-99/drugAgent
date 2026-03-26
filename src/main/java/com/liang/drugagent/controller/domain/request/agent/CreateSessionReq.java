package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionReq {

    /**
     * 会话标题，默认值为"新对话"。
     */
    private String title;

    /**
     * 场景标识，用于区分不同业务场景。
     */
    private String scene;

    /**
     * 用户ID，MVP场景下可选。
     */
    private String userId;
}
