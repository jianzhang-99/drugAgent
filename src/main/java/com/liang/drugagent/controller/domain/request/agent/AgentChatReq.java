package com.liang.drugagent.controller.domain.request.agent;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Drug Agent 请求对象。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatReq {

    /** 会话ID，用于关联历史对话 */
    private String sessionId;

    /** 用户ID，用于数据隔离 */
    private String userId;

    /** 用户查询内容 */
    private String query;

    /** 场景提示，用于路由决策 */
    private String sceneHint;

    /** 已上传文件的ID列表 */
    private List<String> fileIds;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

}
