package com.liang.drugagent.shared.rag.controller;

import com.liang.drugagent.TestConfig;
import com.liang.drugagent.controller.domain.request.knowledge.KnowledgeAskReq;
import com.liang.drugagent.controller.domain.request.knowledge.KnowledgeIngestTextReq;
import com.liang.drugagent.controller.domain.response.knowledge.KnowledgeAskResp;
import com.liang.drugagent.controller.domain.response.knowledge.KnowledgeIngestResp;
import com.liang.drugagent.shared.model.Result;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.RagFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import com.liang.drugagent.shared.rag.mapper.RagFileMapper;
import com.liang.drugagent.shared.rag.model.RagDocument;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import com.liang.drugagent.shared.rag.service.IngestService;
import com.liang.drugagent.shared.rag.service.RagService;
import com.liang.drugagent.shared.rag.service.TextExtractor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 知识库管理接口测试。
 *
 * <p>使用 @WebMvcTest 进行切片测试，只加载 RagController 及其依赖。
 * 注意：由于 Spring Boot 配置问题，此类测试暂时禁用，将在后续版本中修复。</p>
 */
@WebMvcTest(controllers = RagController.class)
@Import(TestConfig.class)
@Disabled("由于 Spring Boot 配置问题暂时禁用，请使用集成测试代替")
@DisplayName("知识库管理接口测试")
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;

    @MockBean
    private IngestService ingestService;

    @MockBean
    private TextExtractor textExtractor;

    @MockBean
    private TencentCosStorageService cosStorageService;

    @MockBean
    private OssFileMapper ossFileMapper;

    @MockBean
    private RagFileMapper ragFileMapper;

    // ========== 文件上传入库测试 ==========

    @Test
    @DisplayName("shouldUploadFileSuccessfullyWhenValidFileProvided")
    void shouldUploadFileSuccessfullyWhenValidFileProvided() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "这是一份测试文档内容，包含用于RAG检索的文本段落。".getBytes()
        );

        doNothing().when(ingestService).ingest(any(MockMultipartFile.class), anyString(), anyString(), any(), any(), any());

        mockMvc.perform(multipart("/knowledge/ingest/file")
                        .file(file)
                        .param("orgId", "test-org-001")
                        .param("title", "测试文档"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.message").value("文件入库成功: test-document.txt"));

        verify(ingestService).ingest(any(), eq("测试文档"), eq("test-org-001"), any(), any(), any());
    }

    @Test
    @DisplayName("shouldReturnErrorWhenOrgIdMissing")
    void shouldReturnErrorWhenOrgIdMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "测试内容".getBytes()
        );

        mockMvc.perform(multipart("/knowledge/ingest/file")
                        .file(file)
                        .param("orgId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("orgId 不能为空"));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenFileIsEmpty")
    void shouldReturnErrorWhenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/knowledge/ingest/file")
                        .file(emptyFile)
                        .param("orgId", "test-org-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("file 不能为空"));
    }

    // ========== 文本入库测试 ==========

    @Test
    @DisplayName("shouldIngestTextSuccessfullyWhenValidRequest")
    void shouldIngestTextSuccessfullyWhenValidRequest() throws Exception {
        String requestBody = """
                {
                    "title": "测试文本",
                    "content": "这是一段用于测试RAG入库的文本内容。",
                    "orgId": "test-org-001",
                    "scene": "tender_review",
                    "docType": "REGULATION"
                }
                """;

        doNothing().when(ingestService).ingest(any(RagDocument.class));

        mockMvc.perform(post("/knowledge/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("入库成功"))
                .andExpect(jsonPath("$.data.sourceId").exists());

        verify(ingestService).ingest(argThat(doc ->
                doc.getOrgId().equals("test-org-001") &&
                doc.getScene().equals("tender_review") &&
                doc.getDocType().equals("REGULATION")
        ));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenOrgIdMissingInTextIngest")
    void shouldReturnErrorWhenOrgIdMissingInTextIngest() throws Exception {
        String requestBody = """
                {
                    "title": "测试文本",
                    "content": "测试内容",
                    "orgId": ""
                }
                """;

        mockMvc.perform(post("/knowledge/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("orgId 不能为空"));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenContentEmptyInTextIngest")
    void shouldReturnErrorWhenContentEmptyInTextIngest() throws Exception {
        String requestBody = """
                {
                    "title": "测试文本",
                    "content": "",
                    "orgId": "test-org-001"
                }
                """;

        mockMvc.perform(post("/knowledge/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("content 不能为空"));
    }

    // ========== 知识问答测试 ==========

    @Test
    @DisplayName("shouldAnswerQuestionSuccessfully")
    void shouldAnswerQuestionSuccessfully() throws Exception {
        String requestBody = """
                {
                    "question": "什么是串通投标？",
                    "orgId": "test-org-001",
                    "scene": "tender_review",
                    "topK": 3
                }
                """;

        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(com.liang.drugagent.shared.rag.model.RagDecision.ANSWERED)
                .reason(com.liang.drugagent.shared.rag.model.RagReason.HIT)
                .answer("串通投标是指投标人之间相互串通，抬高或压低投标报价的行为。")
                .citations(Collections.emptyList())
                .build();

        when(ragService.query(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/knowledge/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("串通投标是指投标人之间相互串通，抬高或压低投标报价的行为。"))
                .andExpect(jsonPath("$.data.decision").value("ANSWERED"));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenOrgIdMissingInAsk")
    void shouldReturnErrorWhenOrgIdMissingInAsk() throws Exception {
        String requestBody = """
                {
                    "question": "什么是串通投标？",
                    "orgId": ""
                }
                """;

        mockMvc.perform(post("/knowledge/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("orgId 不能为空"));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenQuestionEmptyInAsk")
    void shouldReturnErrorWhenQuestionEmptyInAsk() throws Exception {
        String requestBody = """
                {
                    "question": "",
                    "orgId": "test-org-001"
                }
                """;

        mockMvc.perform(post("/knowledge/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("question 不能为空"));
    }

    // ========== 仅检索测试 ==========

    @Test
    @DisplayName("shouldSearchSuccessfullyWithoutAnswerGeneration")
    void shouldSearchSuccessfullyWithoutAnswerGeneration() throws Exception {
        String requestBody = """
                {
                    "question": "围标的法律后果",
                    "orgId": "test-org-001",
                    "topK": 5
                }
                """;

        RagQueryResponse mockResponse = RagQueryResponse.builder()
                .decision(com.liang.drugagent.shared.rag.model.RagDecision.ANSWERED)
                .reason(com.liang.drugagent.shared.rag.model.RagReason.HIT)
                .citations(Collections.emptyList())
                .build();

        when(ragService.query(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").doesNotExist());

        verify(ragService).query(argThat(req -> !Boolean.TRUE.equals(req.getNeedGenerateAnswer())));
    }

    // ========== 文件删除测试 ==========

    @Test
    @DisplayName("shouldDeleteFileSuccessfully")
    void shouldDeleteFileSuccessfully() throws Exception {
        when(ragFileMapper.findByOssId("oss-123")).thenReturn(Optional.of(
                RagFile.builder()
                        .ossId("oss-123")
                        .sourceId("DOC-ABC123")
                        .deleted(0)
                        .build()
        ));

        doNothing().when(ingestService).deleteBySourceId(anyString());
        doNothing().when(ragFileMapper).updateById(any(RagFile.class));

        mockMvc.perform(delete("/knowledge/files/oss-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(ingestService).deleteBySourceId("DOC-ABC123");
        verify(ragFileMapper).updateById(argThat((RagFile rf) -> rf.getDeleted() == 1 && rf.getStatus() == 2));
    }

    @Test
    @DisplayName("shouldReturnErrorWhenOssIdNotFound")
    void shouldReturnErrorWhenOssIdNotFound() throws Exception {
        when(ragFileMapper.findByOssId("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/knowledge/files/non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("未找到 rag_file 关联记录，可能该文件未入库"));
    }

    // ========== 持久化测试 ==========

    @Test
    @DisplayName("shouldPersistSuccessfully")
    void shouldPersistSuccessfully() throws Exception {
        doNothing().when(ingestService).save();

        mockMvc.perform(post("/knowledge/persist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(ingestService).save();
    }
}
