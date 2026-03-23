package com.liang.drugagent.domain.resp;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答响应。
 */
@Getter
@Setter
public class KnowledgeAskResp {

    private String answer;
    private String decision;
    private String reason;
    private String riskLevel;
    private List<Citation> citations = new ArrayList<>();

    @Getter
    @Setter
    public static class Citation {
        private String sourceId;
        private String sourceTitle;
        private String chunkId;
        private String snippet;
        private Double score;
    }
}
