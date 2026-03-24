package com.liang.drugagent.tool.executor;

import com.liang.drugagent.tool.document.DocumentParseTool;
import com.liang.drugagent.tool.document.model.DocumentParseResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 工具执行器。
 *
 * @author liangjiajian
 */
@Service
public class ToolExecutor {

    private final DocumentParseTool documentParseTool;

    public ToolExecutor(DocumentParseTool documentParseTool) {
        this.documentParseTool = documentParseTool;
    }

    /**
     * 解析文档。
     */
    public DocumentParseResult parseDocument(String docId, String filename, InputStream inputStream) throws IOException {
        return documentParseTool.parse(docId, filename, inputStream);
    }
}
