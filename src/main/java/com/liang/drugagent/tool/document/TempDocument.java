package com.liang.drugagent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示一次请求中的临时文件。
 *
 * <p>仅在本次请求内使用，不承载持久化语义。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempDocument {

    /**
     * 本次请求内唯一标识，用于关联。
     */
    private String documentId;

    /**
     * 文件名。
     */
    private String filename;

    /**
     * 文件二进制内容。
     */
    private byte[] content;
}
