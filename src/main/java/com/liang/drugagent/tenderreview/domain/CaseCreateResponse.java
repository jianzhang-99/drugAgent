package com.liang.drugagent.tenderreview.domain;

import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseCreateResponse {

    /** 创建的任务 ID */
    private String caseId;

    /** 任务状态 */
    private CaseStatus status;

    /** 文档 ID 列表（与上传文件顺序对应） */
    private List<String> documentIds;

    /** 响应消息 */
    private String message;
}
