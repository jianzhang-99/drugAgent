package com.liang.drugagent.controller.domain.request.knowledge;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * RAG 问答请求。
 */
@Getter
@Setter
public class KnowledgeAskReq {

    private String question;
    private String scene;
    private String subScene;
    private String docType;
    private String orgId;
    private List<String> topicTags;
    private Integer topK;
    private String sessionId;
}
