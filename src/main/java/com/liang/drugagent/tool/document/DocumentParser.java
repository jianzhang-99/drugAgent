package com.liang.drugagent.tool.document;

/**
 * 文档解析器接口。
 *
 * <p>按文件类型提供统一的解析能力。</p>
 */
public interface DocumentParser {

    /**
     * 判断是否支持解析给定文件。
     *
     * @param filename 文件名
     * @return 是否支持
     */
    boolean supports(String filename);

    /**
     * 解析临时文档。
     *
     * @param document 临时文档
     * @return 解析后的文档
     */
    ParsedDocument parse(TempDocument document);
}
