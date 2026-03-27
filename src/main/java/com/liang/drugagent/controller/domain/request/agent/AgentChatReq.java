package com.liang.drugagent.controller.domain.request.agent;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

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

    /** 用户查询内容 */
    private String query;

    /** 场景提示，用于路由决策 */
    private String sceneHint;

    /** 已上传文件的ID列表 */
    private List<String> fileIds;

    /** 模型选择（minimax/dashscope），为空则使用默认 */
    private String model;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 上传的文件数组 */
    private MultipartFile[] files;

}
