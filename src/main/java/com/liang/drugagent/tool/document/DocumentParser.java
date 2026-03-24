package com.liang.drugagent.tool.document.parser;

import com.liang.drugagent.tool.document.model.DocumentParseResult;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析器接口。
 *
 * @author liangjiajian
 */
public interface DocumentParser {

    /**
     * 判断是否支持该文件类型。
     *
     * @param fileType 文件类型（如 "docx", "doc", "md"）
     * @return 是否支持
     */
    boolean supports(String fileType);

    /**
     * 解析文档输入流。
     *
     * @param inputStream 文档输入流（调用方负责关闭）
     * @param docId       文档 ID
     * @return 解析结果
     * @throws IOException 读取失败时抛出
     */
    DocumentParseResult parse(InputStream inputStream, String docId) throws IOException;
}
