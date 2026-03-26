package com.liang.drugagent.controller.domain.request.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 文本入库请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestTextReq {

    /** 文档标题 */
    private String title;

    /** 文档内容 */
    private String content;

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

    /** 来源ID */
    private String sourceId;

    /** 版本号 */
    private String version;
}
