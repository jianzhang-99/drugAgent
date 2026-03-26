package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话消息请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageReq {

    /** 消息角色 */
    private String role;

    /** 消息内容 */
    private String content;
}
