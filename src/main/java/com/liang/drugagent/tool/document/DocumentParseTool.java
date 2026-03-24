package com.liang.drugagent.tool.document;

import com.liang.drugagent.tool.document.model.DocumentParseResult;
import com.liang.drugagent.tool.document.parser.DocumentParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * 文档解析工具。
 *
 * <p>统一入口，根据文件类型自动路由到合适的解析器。</p>
 *
 * @author liangjiajian
 */
@Component
public class DocumentParseTool {

    public static final String TOOL_NAME = "document.parse";

    private final List<DocumentParser> parsers;

    public DocumentParseTool(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 解析文档。
     *
     * @param docId       文档 ID
     * @param filename    原始文件名
     * @param inputStream 文件流
     * @return 解析结果
     * @throws IOException 文件读取失败时抛出
     */
    public DocumentParseResult parse(String docId, String filename, InputStream inputStream) throws IOException {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String fileType = extractFileType(lowerName);

        for (DocumentParser parser : parsers) {
            if (parser.supports(fileType)) {
                return parser.parse(inputStream, docId);
            }
        }
        throw new IllegalArgumentException("暂不支持解析该文件类型: " + filename);
    }

    private String extractFileType(String filename) {
        if (filename.endsWith(".docx")) return "docx";
        if (filename.endsWith(".doc")) return "doc";
        if (filename.endsWith(".md")) return "md";
        return "";
    }
}
