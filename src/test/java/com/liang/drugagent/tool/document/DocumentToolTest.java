package com.liang.drugagent.tool.document;

import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentTool 单元测试。
 *
 * <p>验证文档工具入口功能：
 * <ol>
 *   <li>正常解析一份 markdown 文件</li>
 *   <li>正常解析多份混合格式文件（.docx, .doc, .md）</li>
 *   <li>空文件列表 → 返回 empty result</li>
 *   <li>不支持的格式 → 加入 errors 列表</li>
 * </ol>
 */
class DocumentToolTest {

    private DocumentTool documentTool;
    private DocumentTextNormalizer normalizer;
    private MarkdownDocumentParser markdownParser;

    @BeforeEach
    void setUp() {
        normalizer = new DocumentTextNormalizer();
        markdownParser = new MarkdownDocumentParser();
        // DocumentTool 依赖 Spring 注入的 parsers 列表，这里手动构造
        documentTool = new DocumentTool(List.of(markdownParser), normalizer);
    }

    @Test
    void should正常解析一份Markdown文件() {
        String content = "# 投标文件\n\n这是正文内容。";
        TempDocument doc = TempDocument.builder()
                .documentId("doc-1")
                .filename("bid.md")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();

        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of(doc))
                .build();

        DocumentToolResult result = documentTool.parse(req);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(1, result.getDocuments().size());
        assertEquals("doc-1", result.getDocuments().get(0).getDocumentId());
        assertEquals(content, result.getDocuments().get(0).getPlainText());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void should正常解析多份混合格式文件() {
        // 创建多份不同格式的文档
        TempDocument mdDoc = TempDocument.builder()
                .documentId("doc-md")
                .filename("tender1.md")
                .content("# 标书1\n\n正文内容".getBytes(StandardCharsets.UTF_8))
                .build();

        TempDocument docxDoc = TempDocument.builder()
                .documentId("doc-docx")
                .filename("tender2.docx")
                .content("docx content placeholder".getBytes(StandardCharsets.UTF_8))
                .build();

        TempDocument docDoc = TempDocument.builder()
                .documentId("doc-doc")
                .filename("tender3.doc")
                .content("doc content placeholder".getBytes(StandardCharsets.UTF_8))
                .build();

        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of(mdDoc, docxDoc, docDoc))
                .build();

        DocumentToolResult result = documentTool.parse(req);

        // 只有 md 文件能成功解析
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getFailureCount());
        assertEquals(1, result.getDocuments().size());
        assertEquals("doc-md", result.getDocuments().get(0).getDocumentId());
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("tender2.docx"));
        assertTrue(result.getErrors().get(1).contains("tender3.doc"));
    }

    @Test
    void should返回空结果_当文件列表为空() {
        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of())
                .build();

        DocumentToolResult result = documentTool.parse(req);

        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertTrue(result.getDocuments().isEmpty());
    }

    @Test
    void should返回空结果_当请求为null() {
        DocumentToolResult result = documentTool.parse(null);

        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getDocuments().isEmpty());
    }

    @Test
    void should返回空结果_当文档列表为null() {
        DocumentToolReq req = DocumentToolReq.builder()
                .documents(null)
                .build();

        DocumentToolResult result = documentTool.parse(req);

        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getDocuments().isEmpty());
    }

    @Test
    void should处理不支持的格式() {
        TempDocument unsupportedDoc = TempDocument.builder()
                .documentId("doc-pdf")
                .filename("document.pdf")
                .content("pdf content".getBytes(StandardCharsets.UTF_8))
                .build();

        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of(unsupportedDoc))
                .build();

        DocumentToolResult result = documentTool.parse(req);

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertTrue(result.getDocuments().isEmpty());
        assertEquals(1, result.getErrors().size());
        // 不支持的文件格式时，ParsedDocument 的 plainText 为空，
        // DocumentTool 检测到后会添加 "文件解析失败或内容为空" 错误
        assertTrue(result.getErrors().get(0).contains("文件解析失败或内容为空"));
    }

    @Test
    void should处理混合格式_包含成功和失败() {
        TempDocument md1 = TempDocument.builder()
                .documentId("doc-1")
                .filename("tender-a.md")
                .content("# 标书A\n\n投标人：蓝天博科".getBytes(StandardCharsets.UTF_8))
                .build();

        TempDocument unsupported = TempDocument.builder()
                .documentId("doc-2")
                .filename("tender-b.pdf")
                .content("pdf".getBytes(StandardCharsets.UTF_8))
                .build();

        TempDocument md2 = TempDocument.builder()
                .documentId("doc-3")
                .filename("tender-c.md")
                .content("# 标书C\n\n投标人：云极光".getBytes(StandardCharsets.UTF_8))
                .build();

        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of(md1, unsupported, md2))
                .build();

        DocumentToolResult result = documentTool.parse(req);

        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(2, result.getDocuments().size());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void should对解析后的文本进行清洗() {
        // 原始内容包含多个空白和换行
        String rawContent = "标题\r\n\r\n    多个   空格\t\t内容\n\n\n\n更多内容";
        TempDocument doc = TempDocument.builder()
                .documentId("doc-1")
                .filename("test.md")
                .content(rawContent.getBytes(StandardCharsets.UTF_8))
                .build();

        DocumentToolReq req = DocumentToolReq.builder()
                .documents(List.of(doc))
                .build();

        DocumentToolResult result = documentTool.parse(req);

        ParsedDocument parsed = result.getDocuments().get(0);
        // normalizedText 应该被清洗过
        assertNotNull(parsed.getNormalizedText());
        assertFalse(parsed.getNormalizedText().contains("\r"));
        assertFalse(parsed.getNormalizedText().contains("\t"));
    }
}
