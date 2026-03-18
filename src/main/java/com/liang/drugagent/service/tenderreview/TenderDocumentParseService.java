package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Service
public class TenderDocumentParseService {

    private final TenderDocxParser docxParser;
    private final TenderDocParser docParser;
    private final TenderMarkdownParser markdownParser;

    public TenderDocumentParseService(TenderDocxParser docxParser,
                                      TenderDocParser docParser,
                                      TenderMarkdownParser markdownParser) {
        this.docxParser = docxParser;
        this.docParser = docParser;
        this.markdownParser = markdownParser;
    }

    /**
     * 解析文档输入流，返回解析结果。
     *
     * @param docId       文档 ID
     * @param inputStream 文件流（调用方负责关闭）
     * @param filename    原始文件名，用于判断解析器
     * @return 解析结果
     * @throws IOException 文件读取失败时抛出
     */
    public TenderDocumentParseResult parseDocument(String docId, String filename, InputStream inputStream) throws IOException {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) {
            return docxParser.parse(inputStream, docId);
        }
        if (lowerName.endsWith(".doc")) {
            return docParser.parse(inputStream, docId);
        }
        if (lowerName.endsWith(".md")) {
            return markdownParser.parse(inputStream, docId);
        }
        throw new IllegalArgumentException("暂不支持解析该文件类型: " + filename);
    }
}
