package com.liang.drugagent.tenderreview.domain;

import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseDocument {

    /** 文档唯一 ID */
    private String docId;

    /** 所属任务 ID */
    private String caseId;

    /** 原始文件名 */
    private String filename;

    /** 文档处理状态 */
    private CaseStatus status;
}
