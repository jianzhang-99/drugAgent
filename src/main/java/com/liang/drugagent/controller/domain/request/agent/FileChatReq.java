package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Agent 文件上传对话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileChatReq {

    /** 用户查询内容 */
    private String query;

    /** 场景提示，用于路由决策 */
    private String sceneHint;

    /** 会话ID，用于关联历史对话 */
    private String sessionId;

    /** 用户ID，用于数据隔离 */
    private String userId;

    /** 提交人，默认为 anonymous */
    private String submittedBy = "anonymous";

    /** 上传的文件数组 */
    private MultipartFile[] files;
}
