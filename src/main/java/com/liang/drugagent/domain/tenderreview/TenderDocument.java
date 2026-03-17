package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 参与审查的文档元数据。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class TenderDocument {

    /** 文档唯一 ID。 */
    private String documentId;
    /** 文档展示名称。 */
    private String documentName;
    /** 文件类型，例如 docx、pdf。 */
    private String fileType;
}
