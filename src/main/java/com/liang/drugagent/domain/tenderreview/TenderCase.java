package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

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
}
