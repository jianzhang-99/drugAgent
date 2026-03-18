package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.enums.TenderCaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenderCaseServiceTest {

    private TenderCaseService caseService;
    private InMemoryTenderCaseStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTenderCaseStore();
        caseService = new TenderCaseService(store);
    }

    @Test
    void createCaseSuccess() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("a.docx", "b.docx"))
                .submittedBy("tester")
                .build();

        TenderCaseCreateResp resp = caseService.createCase(req);

        assertThat(resp.getCaseId()).isNotBlank();
        assertThat(resp.getStatus()).isEqualTo(TenderCaseStatus.PENDING);
        assertThat(resp.getDocumentIds()).hasSize(2);
        assertThat(resp.getMessage()).contains("2");
        assertThat(store.caseCount()).isEqualTo(1);
        assertThat(store.documentCount()).isEqualTo(2);
    }

    @Test
    void createCaseWithThreeFiles() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("a.docx", "b.docx", "c.docx"))
                .submittedBy("user")
                .build();

        TenderCaseCreateResp resp = caseService.createCase(req);
        assertThat(resp.getDocumentIds()).hasSize(3);
        assertThat(store.documentCount()).isEqualTo(3);
    }

    @Test
    void rejectsNullFilenames() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(null)
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要 2 份标书文件");
    }

    @Test
    void rejectsOneFile() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("only.docx"))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要 2 份标书文件");
    }

    @Test
    void rejectsUnsupportedFile() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("a.docx", "b.pdf"))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 doc、docx、md 格式文件");
    }

    @Test
    void rejectsBlankFilename() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("a.docx", "   "))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFilename() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(Arrays.asList("a.docx", null))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filenameIsCaseInsensitiveDocxCheck() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("A.DOCX", "B.Docx"))
                .submittedBy("user")
                .build();

        TenderCaseCreateResp resp = caseService.createCase(req);
        assertThat(resp.getDocumentIds()).hasSize(2);
    }

    @Test
    void supportsDocAndMdFiles() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("A.DOC", "readme.md"))
                .submittedBy("user")
                .build();

        TenderCaseCreateResp resp = caseService.createCase(req);
        assertThat(resp.getDocumentIds()).hasSize(2);
    }

    @Test
    void storeAndRetrieveFileContent() {
        String docId = "doc-abc";
        byte[] bytes = "hello docx".getBytes();
        caseService.storeFileContent(docId, bytes);

        Optional<byte[]> result = caseService.getFileContent(docId);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(bytes);
    }

    @Test
    void getFileContentReturnsEmptyWhenAbsent() {
        assertThat(caseService.getFileContent("no-such")).isEmpty();
    }

    @Test
    void documentIdsAreUnique() {
        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(List.of("a.docx", "b.docx"))
                .submittedBy("user")
                .build();
        TenderCaseCreateResp resp = caseService.createCase(req);

        assertThat(resp.getDocumentIds().get(0))
                .isNotEqualTo(resp.getDocumentIds().get(1));
    }

    @Test
    void nullRequestThrows() {
        assertThatThrownBy(() -> caseService.createCase(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
