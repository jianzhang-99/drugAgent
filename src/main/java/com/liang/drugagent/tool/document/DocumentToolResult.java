package com.liang.drugagent.tool.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档工具统一返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentToolResult {

    /**
     * 成功解析的文档列表。
     */
    @Builder.Default
    private List<ParsedDocument> documents = new ArrayList<>();

    /**
     * 警告信息列表（如部分解析成功）。
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * 错误信息列表（如完全解析失败）。
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * 成功解析数量。
     */
    @Builder.Default
    private Integer successCount = 0;

    /**
     * 解析失败数量。
     */
    @Builder.Default
    private Integer failureCount = 0;

    public static DocumentToolResult empty() {
        return DocumentToolResult.builder().build();
    }
}
