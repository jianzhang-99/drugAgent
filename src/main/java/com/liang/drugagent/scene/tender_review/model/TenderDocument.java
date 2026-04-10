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

    // ===== 文件指纹元数据（用于 W-M7 元数据聚集性检测）=====
    /** 文档作者（从文件属性提取）。 */
    private String author;
    /** 文档创建时间。 */
    private String createDate;
    /** 文档最后修改时间。 */
    private String lastModified;
    /** 创建文档的应用程序（如 Microsoft Word 16.0）。 */
    private String application;
    /** 原始元数据文本（完整注释块原文）。 */
    private String metadataRaw;
}
