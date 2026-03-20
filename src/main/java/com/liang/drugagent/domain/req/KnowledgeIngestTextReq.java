package com.liang.drugagent.domain.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * RAG 文本入库请求。
 */
@Getter
@Setter
public class KnowledgeIngestTextReq {

    private String title;
    private String content;
    private String scene;
    private String subScene;
    private String docType;
    private String orgId;
    private List<String> topicTags;
    private String sourceId;
    private String version;
}
