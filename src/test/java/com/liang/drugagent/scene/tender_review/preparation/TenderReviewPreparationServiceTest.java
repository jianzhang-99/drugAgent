package com.liang.drugagent.scene.tender_review.preparation;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.tool.document.DocumentTool;
import com.liang.drugagent.tool.document.DocumentToolReq;
import com.liang.drugagent.tool.document.DocumentToolResult;
import com.liang.drugagent.tool.document.ParsedDocument;
import com.liang.drugagent.tool.document.TempDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenderReviewPreparationService 单元测试。
 *
 * <p>验证数据准备服务功能：
 * <ol>
 *   <li>有上传文件时 → 调用 DocumentTool 并构建 TenderReviewData</li>
 *   <li>文件数<2 → 返回 null（数据不足）</li>
 *   <li>文件解析全部失败 → 返回 null</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TenderReviewPreparationServiceTest {

    @Mock
    private TenderReviewDataAssembler dataAssembler;

    @Mock
    private TenderCaseService caseService;

    @Mock
    private DocumentTool documentTool;

    private TenderReviewPreparationService preparationService;

    @BeforeEach
    void setUp() {
        preparationService = new TenderReviewPreparationService(dataAssembler, caseService, documentTool);
    }

    @Test
    void should有上传文件时调用DocumentTool并构建数据() {
        // 准备两份上传文件
        MockMultipartFile file1 = new MockMultipartFile(
                "file1", "tender1.md", "text/markdown",
                "# 投标书1\n\n投标人：蓝天博科".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file2", "tender2.md", "text/markdown",
                "# 投标书2\n\n投标人：云极光".getBytes(StandardCharsets.UTF_8)
        );

        AgentChatReq req = AgentChatReq.builder()
                .files(new MockMultipartFile[]{file1, file2})
                .build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "query",
                null,
                null,
                new HashMap<>()
        );

        // Mock DocumentTool 返回成功解析结果
        ParsedDocument parsedDoc1 = ParsedDocument.builder()
                .documentId("doc-1")
                .filename("tender1.md")
                .fileType("md")
                .plainText("# 投标书1\n\n投标人：蓝天博科")
                .normalizedText("投标书1 投标人：蓝天博科")
                .build();

        ParsedDocument parsedDoc2 = ParsedDocument.builder()
                .documentId("doc-2")
                .filename("tender2.md")
                .fileType("md")
                .plainText("# 投标书2\n\n投标人：云极光")
                .normalizedText("投标书2 投标人：云极光")
                .build();

        DocumentToolResult toolResult = DocumentToolResult.builder()
                .documents(List.of(parsedDoc1, parsedDoc2))
                .successCount(2)
                .failureCount(0)
                .build();

        when(documentTool.parse(any(DocumentToolReq.class))).thenReturn(toolResult);
        when(dataAssembler.resolve(any(ParsedDocument[].class), anyString()))
                .thenReturn(createValidTenderReviewData());

        TenderReviewData result = preparationService.prepare(context, req);

        assertNotNull(result);
        assertEquals(2, result.getDocuments().size());

        // 验证 DocumentTool 被调用
        ArgumentCaptor<DocumentToolReq> captor = ArgumentCaptor.forClass(DocumentToolReq.class);
        verify(documentTool).parse(captor.capture());
        assertEquals(2, captor.getValue().getDocuments().size());
    }

    @Test
    void should文件数小于2时返回Null() {
        // 只上传一份文件
        MockMultipartFile file1 = new MockMultipartFile(
                "file1", "tender1.md", "text/markdown",
                "# 投标书1".getBytes(StandardCharsets.UTF_8)
        );

        AgentChatReq req = AgentChatReq.builder()
                .files(new MockMultipartFile[]{file1})
                .build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "query",
                null,
                null,
                new HashMap<>()
        );

        TenderReviewData result = preparationService.prepare(context, req);

        assertNull(result);
        // DocumentTool 不应该被调用
        verify(documentTool, never()).parse(any());
    }

    @Test
    void should文件解析全部失败时返回Null() {
        // 准备两份文件但解析全部失败
        MockMultipartFile file1 = new MockMultipartFile(
                "file1", "tender1.pdf", "application/pdf",
                "pdf content".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file2", "tender2.pdf", "application/pdf",
                "pdf content".getBytes(StandardCharsets.UTF_8)
        );

        AgentChatReq req = AgentChatReq.builder()
                .files(new MockMultipartFile[]{file1, file2})
                .build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "query",
                null,
                null,
                new HashMap<>()
        );

        // Mock DocumentTool 返回全部失败
        DocumentToolResult toolResult = DocumentToolResult.builder()
                .documents(new ArrayList<>())
                .successCount(0)
                .failureCount(2)
                .errors(List.of("不支持的文件格式: pdf", "不支持的文件格式: pdf"))
                .build();

        when(documentTool.parse(any(DocumentToolReq.class))).thenReturn(toolResult);

        TenderReviewData result = preparationService.prepare(context, req);

        assertNull(result);
    }

    @Test
    void should检查数据是否满足最低要求_有效数据() {
        TenderReviewData data = createValidTenderReviewData();

        assertTrue(preparationService.hasEnoughDocuments(data));
    }

    @Test
    void should检查数据是否满足最低要求_数据为Null() {
        assertFalse(preparationService.hasEnoughDocuments(null));
    }

    @Test
    void should检查数据是否满足最低要求_文档列表为空() {
        TenderReviewData data = new TenderReviewData();
        data.setDocuments(new ArrayList<>());

        assertFalse(preparationService.hasEnoughDocuments(data));
    }

    @Test
    void should检查数据是否满足最低要求_只有一份文档() {
        TenderReviewData data = new TenderReviewData();
        List<TenderDocument> docs = new ArrayList<>();
        docs.add(new TenderDocument());
        data.setDocuments(docs);

        assertFalse(preparationService.hasEnoughDocuments(data));
    }

    @Test
    void should有上传文件但有效解析数小于2时返回Null() {
        // 准备两份文件，但只有一份能成功解析
        MockMultipartFile file1 = new MockMultipartFile(
                "file1", "tender1.md", "text/markdown",
                "# 投标书1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file2", "tender2.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8)
        );

        AgentChatReq req = AgentChatReq.builder()
                .files(new MockMultipartFile[]{file1, file2})
                .build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "query",
                null,
                null,
                new HashMap<>()
        );

        // Mock DocumentTool 返回只有 1 个成功
        ParsedDocument parsedDoc1 = ParsedDocument.builder()
                .documentId("doc-1")
                .filename("tender1.md")
                .fileType("md")
                .plainText("# 投标书1")
                .normalizedText("投标书1")
                .build();

        DocumentToolResult toolResult = DocumentToolResult.builder()
                .documents(List.of(parsedDoc1))
                .successCount(1)
                .failureCount(1)
                .build();

        when(documentTool.parse(any(DocumentToolReq.class))).thenReturn(toolResult);

        TenderReviewData result = preparationService.prepare(context, req);

        assertNull(result);
    }

    @Test
    void shouldSetReadablePreparationErrorWhenNoFiles() {
        AgentChatReq req = AgentChatReq.builder().build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "帮我审查一份标书可以吗",
                null,
                null,
                new HashMap<>()
        );

        TenderReviewData result = preparationService.prepare(context, req);

        assertNull(result);
        assertEquals("当前还没有检测到可审查的标书文件。请先上传至少2份标书文件，我再继续为你审查围标风险。",
                context.getMetadata().get("preparationError"));
    }

    @Test
    void shouldSetReadablePreparationErrorWhenOnlyOneFileUploaded() {
        MockMultipartFile file1 = new MockMultipartFile(
                "file1", "tender1.md", "text/markdown",
                "# 投标书".getBytes(StandardCharsets.UTF_8)
        );

        AgentChatReq req = AgentChatReq.builder()
                .files(new MockMultipartFile[]{file1})
                .build();

        AgentChatContext context = AgentChatContext.fromToolRequest(
                "session-123",
                "query",
                null,
                null,
                new HashMap<>()
        );

        TenderReviewData result = preparationService.prepare(context, req);

        assertNull(result);
        assertEquals("当前还没有检测到可审查的标书文件。请先上传至少2份标书文件，我再继续为你审查围标风险。",
                context.getMetadata().get("preparationError"));
    }

    private TenderReviewData createValidTenderReviewData() {
        TenderReviewData data = new TenderReviewData();

        List<TenderDocument> docs = new ArrayList<>();
        TenderDocument doc1 = new TenderDocument();
        doc1.setDocumentId("doc-1");
        doc1.setFilename("tender1.md");
        docs.add(doc1);

        TenderDocument doc2 = new TenderDocument();
        doc2.setDocumentId("doc-2");
        doc2.setFilename("tender2.md");
        docs.add(doc2);

        data.setDocuments(docs);

        var tenderCase = new com.liang.drugagent.scene.tender_review.model.TenderCase();
        tenderCase.setCaseId("case-123");
        tenderCase.setScene("tender_review");
        data.setACase(tenderCase);

        return data;
    }
}
