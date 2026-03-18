package com.liang.drugagent.tenderreview.service;

import com.liang.drugagent.tenderreview.parser.DocxParser;
import com.liang.drugagent.tenderreview.parser.DocumentParseResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class DocumentParseService {

    private final DocxParser docxParser;

    public DocumentParseService(DocxParser docxParser) {
        this.docxParser = docxParser;
    }

    /**
     * 解析文档输入流，返回解析结果。
     *
     * @param docId       文档 ID
     * @param inputStream docx 文件流（调用方负责关闭）
     * @return 解析结果
     * @throws IOException 文件读取失败时抛出
     */
    public DocumentParseResult parseDocument(String docId, InputStream inputStream) throws IOException {
        return docxParser.parse(inputStream, docId);
    }
}
