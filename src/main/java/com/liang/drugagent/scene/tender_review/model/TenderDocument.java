package com.liang.drugagent.scene.tender_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参与审查的文档元数据。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderDocument {

    /** 所属任务 ID。 */
    private String caseId;
    /** 文档唯一 ID。 */
    private String documentId;
    /** 文档展示名称。 */
    private String documentName;
    /** 原始文件名。 */
    private String filename;
    /** 文件类型，例如 docx、pdf。 */
    private String fileType;
    /** 文档处理状态。 */
    private String status;
}
