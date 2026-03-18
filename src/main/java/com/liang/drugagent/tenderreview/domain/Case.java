package com.liang.drugagent.tenderreview.domain;

import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Case {

    /** 任务唯一 ID */
    private String caseId;

    /** 任务状态 */
    private CaseStatus status;

    /** 提交人 */
    private String submittedBy;

    /** 创建时间 */
    private Instant createdAt;

    /** 关联的文档 ID 列表 */
    private List<String> documentIds;
}
