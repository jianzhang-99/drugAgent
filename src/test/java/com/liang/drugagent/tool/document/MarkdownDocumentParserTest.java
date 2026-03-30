package com.liang.drugagent.tool.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarkdownDocumentParser 单元测试。
 *
 * <p>验证 Markdown 解析器功能：
 * <ol>
 *   <li>支持 .md 和 .markdown 扩展名</li>
 *   <li>不支持其他扩展名</li>
 *   <li>正确解析 Markdown 纯文本内容</li>
 *   <li>处理空内容情况</li>
 * </ol>
 */
class MarkdownDocumentParserTest {

    private MarkdownDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownDocumentParser();
    }

    @Test
    void should支持Md扩展名() {
        assertTrue(parser.supports("document.md"));
        assertTrue(parser.supports("DOCUMENT.MD"));
        assertTrue(parser.supports("Document.Md"));
    }

    @Test
    void should支持Markdown扩展名() {
        assertTrue(parser.supports("document.markdown"));
        assertTrue(parser.supports("DOCUMENT.MARKDOWN"));
        assertTrue(parser.supports("Document.MarkDown"));
    }

    @Test
    void should不支持Docx扩展名() {
        assertFalse(parser.supports("document.docx"));
    }

    @Test
    void should不支持Doc扩展名() {
        assertFalse(parser.supports("document.doc"));
    }

    @Test
    void should不支持Txt扩展名() {
        assertFalse(parser.supports("document.txt"));
    }

    @Test
    void should不支持Pdf扩展名() {
        assertFalse(parser.supports("document.pdf"));
    }

    @Test
    void should不支持空文件名() {
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(""));
    }

    @Test
    void should解析正常Markdown内容() {
        String content = "# 标题\n\n这是正文内容。";
        TempDocument tempDoc = TempDocument.builder()
                .documentId("doc-1")
                .filename("test.md")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();

        ParsedDocument result = parser.parse(tempDoc);

        assertEquals("doc-1", result.getDocumentId());
        assertEquals("test.md", result.getFilename());
        assertEquals("md", result.getFileType());
        assertEquals(content, result.getPlainText());
        // normalizedText 由 DocumentTool 调用 normalizer 后设置，MarkdownDocumentParser 不负责
        assertNull(result.getNormalizedText());
    }

    @Test
    void should解析纯文本Markdown() {
        // Markdown 本质是纯文本，不需要特殊解析
        String content = "这是一份普通的投标文件。\n\n包含多行内容。";
        TempDocument tempDoc = TempDocument.builder()
                .documentId("doc-2")
                .filename("bid.md")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();

        ParsedDocument result = parser.parse(tempDoc);

        assertEquals(content, result.getPlainText());
        // normalizedText 由 DocumentTool 调用 normalizer 后设置
        assertNull(result.getNormalizedText());
    }

    @Test
    void should处理空内容() {
        TempDocument tempDoc = TempDocument.builder()
                .documentId("doc-3")
                .filename("empty.md")
                .content(new byte[0])
                .build();

        ParsedDocument result = parser.parse(tempDoc);

        assertEquals("doc-3", result.getDocumentId());
        assertEquals("", result.getPlainText());
        assertEquals("", result.getNormalizedText());
    }

    @Test
    void should处理null内容() {
        TempDocument tempDoc = TempDocument.builder()
                .documentId("doc-4")
                .filename("null.md")
                .content(null)
                .build();

        ParsedDocument result = parser.parse(tempDoc);

        assertEquals("doc-4", result.getDocumentId());
        assertEquals("", result.getPlainText());
        assertEquals("", result.getNormalizedText());
    }

    @Test
    void should处理UTF8编码内容() {
        String content = "# 医疗监管系统投标书\n\n" +
                "投标人：蓝天博科系统集成有限公司\n" +
                "联系人：林美玲 (13588997766)";
        TempDocument tempDoc = TempDocument.builder()
                .documentId("doc-5")
                .filename("medical-bid.md")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();

        ParsedDocument result = parser.parse(tempDoc);

        assertEquals(content, result.getPlainText());
        // normalizedText 由 DocumentTool 调用 normalizer 后设置
        assertNull(result.getNormalizedText());
    }
}
