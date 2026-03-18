package com.liang.drugagent.tenderreview.controller;

import com.liang.drugagent.tenderreview.domain.CaseCreateResponse;
import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import com.liang.drugagent.tenderreview.parser.DocumentParseResult;
import com.liang.drugagent.tenderreview.service.CaseService;
import com.liang.drugagent.tenderreview.service.DocumentParseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenderReviewController.class)
class TenderReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    @MockBean
    private DocumentParseService documentParseService;

    @Test
    void createCaseReturns201WithTwoFiles() throws Exception {
        CaseCreateResponse resp = CaseCreateResponse.builder()
                .caseId("case-001")
                .status(CaseStatus.PENDING)
                .documentIds(List.of("doc-1", "doc-2"))
                .message("任务创建成功，待解析文档数：2")
                .build();
        when(caseService.createCase(any())).thenReturn(resp);
        doNothing().when(caseService).storeFileContent(anyString(), any());
        when(caseService.getFileContent(anyString())).thenReturn(Optional.of(new byte[0]));

        MockMultipartFile fileA = new MockMultipartFile("files", "a.docx",
                "application/octet-stream", "docx content A".getBytes());
        MockMultipartFile fileB = new MockMultipartFile("files", "b.docx",
                "application/octet-stream", "docx content B".getBytes());

        mockMvc.perform(multipart("/tender-review/cases")
                        .file(fileA)
                        .file(fileB)
                        .param("submittedBy", "tester"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value("case-001"))
                .andExpect(jsonPath("$.documentIds").isArray())
                .andExpect(jsonPath("$.documentIds.length()").value(2));
    }

    @Test
    void createCaseReturns400WhenServiceThrows() throws Exception {
        when(caseService.createCase(any()))
                .thenThrow(new IllegalArgumentException("至少需要 2 份标书文件进行比对审查"));

        MockMultipartFile fileA = new MockMultipartFile("files", "a.docx",
                "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/tender-review/cases")
                        .file(fileA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void parseDocumentReturns200() throws Exception {
        DocumentParseResult parseResult = DocumentParseResult.builder()
                .docId("doc-1")
                .sectionTree(List.of())
                .paragraphBlocks(List.of())
                .tableBlocks(List.of())
                .build();
        when(caseService.getFileContent(eq("doc-1")))
                .thenReturn(Optional.of("fake docx bytes".getBytes()));
        when(documentParseService.parseDocument(eq("doc-1"), any(InputStream.class)))
                .thenReturn(parseResult);

        mockMvc.perform(post("/tender-review/cases/case-001/parse/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docId").value("doc-1"));
    }

    @Test
    void parseDocumentReturns404WhenFileNotFound() throws Exception {
        when(caseService.getFileContent(eq("no-doc")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/tender-review/cases/case-001/parse/no-doc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("未找到文件内容, docId=no-doc"));
    }
}
