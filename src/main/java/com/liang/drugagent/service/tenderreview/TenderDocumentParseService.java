package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class TenderDocumentParseService {

    private final TenderDocxParser docxParser;

    public TenderDocumentParseService(TenderDocxParser docxParser) {
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
    public TenderDocumentParseResult parseDocument(String docId, InputStream inputStream) throws IOException {
        return docxParser.parse(inputStream, docId);
    }
}
