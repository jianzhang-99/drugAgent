package com.liang.drugagent.controller.domain.request.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 问答请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAskReq {

    /** 问题内容 */
    private String question;

    /** 场景标识 */
    private String scene;

    /** 子场景标识 */
    private String subScene;

    /** 文档类型 */
    private String docType;

    /** 机构ID */
    private String orgId;

    /** 主题标签列表 */
    private List<String> topicTags;

    /** 返回结果数量 */
    private Integer topK;

    /** 会话ID */
    private String sessionId;
}
