package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 会话消息请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageReq {

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 消息角色，默认值为 user。
     */
    private String role;

    /**
     * 消息类型。
     */
    private String type;

    /**
     * 扩展元数据。
     */
    private Map<String, Object> metadata;
}
