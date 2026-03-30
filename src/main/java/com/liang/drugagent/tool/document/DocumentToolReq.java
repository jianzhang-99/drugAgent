package com.liang.drugagent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档工具请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentToolReq {

    /**
     * 本次请求中的临时文件列表。
     */
    private List<TempDocument> documents;
}
