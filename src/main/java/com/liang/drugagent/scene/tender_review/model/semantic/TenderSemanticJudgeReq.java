package com.liang.drugagent.scene.tender_review.model.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 语义裁决请求对象。
 * 封装提交给 LLM 进行语义判断的输入数据。
 *
 * @author architect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticJudgeReq {

    /**
     * 规则编码，如 "W-P1"、"W-P4"、"W-M8"。
     */
    private String ruleCode;

    /**
     * 场景标识，如 "TENDER_REVIEW"。
     */
    private String scene;

    /**
     * 案件 ID。
     */
    private String caseId;

    /**
     * 左侧文档 ID。
     */
    private String leftDocumentId;

    /**
     * 右侧文档 ID。
     */
    private String rightDocumentId;

    /**
     * 比对主题，如 "技术方案"、"风险识别"、"商务条款配合"。
     */
    private String compareTopic;

    /**
     * 左侧文档抽取的候选片段列表。
     */
    private List<String> leftSnippets;

    /**
     * 右侧文档抽取的候选片段列表。
     */
    private List<String> rightSnippets;

    /**
     * 额外上下文信息（如章节路径、字段类型等）。
     */
    private Map<String, Object> extraContext;
}
