package com.liang.drugagent.scene.tender_review.model.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义证据对象。
 * 记录 LLM 判断中引用的关键片段及其解释。
 *
 * @author architect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticEvidence {

    /**
     * 文档 ID。
     */
    private String documentId;

    /**
     * 章节路径，如 "第3章/3.1 技术方案"。
     */
    private String chapterPath;

    /**
     * 原文摘录。
     */
    private String excerpt;

    /**
     * 对该摘录的解释，说明为什么它是关键证据。
     */
    private String explanation;
}
