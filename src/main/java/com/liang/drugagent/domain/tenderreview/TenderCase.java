package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 标书审查任务信息。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class TenderCase {

    /** 审查任务 ID。 */
    private String caseId;
    /** 场景标识，通常为 tender_review。 */
    private String scene;
    /** 任务状态。 */
    private String status;
    /** 提交人。 */
    private String submittedBy;
    /** 创建时间。 */
    private Instant createdAt;
    /** 关联的文档 ID 列表。 */
    private List<String> documentIds = new ArrayList<>();
    /** 审查结果（JSON格式存储）。 */
    private String reviewResult;
    /** 风险等级。 */
    private String riskLevel;
    /** 综合评分。 */
    private Integer score;
}
