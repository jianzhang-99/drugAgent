package com.liang.drugagent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一解析后的文档对象。
 *
 * <p>由 DocumentParser 产出，供场景 PreparationService 继续消费。
 * 当前阶段只保留纯文本相关结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    /**
     * 对应 TempDocument.documentId。
     */
    private String documentId;

    /**
     * 原文件名。
     */
    private String filename;

    /**
     * 文件类型，如 docx、doc、md。
     */
    private String fileType;

    /**
     * 提取后的纯文本内容。
     */
    private String plainText;

    /**
     * 经过基础清洗后的文本。
     */
    private String normalizedText;
}
