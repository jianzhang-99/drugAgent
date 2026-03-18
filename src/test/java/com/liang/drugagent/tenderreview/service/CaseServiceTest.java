package com.liang.drugagent.tenderreview.service;

import com.liang.drugagent.tenderreview.domain.CaseCreateRequest;
import com.liang.drugagent.tenderreview.domain.CaseCreateResponse;
import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import com.liang.drugagent.tenderreview.storage.InMemoryCaseStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseServiceTest {

    private CaseService caseService;
    private InMemoryCaseStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCaseStore();
        caseService = new CaseService(store);
    }

    @Test
    void createCaseSuccess() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("a.docx", "b.docx"))
                .submittedBy("tester")
                .build();

        CaseCreateResponse resp = caseService.createCase(req);

        assertThat(resp.getCaseId()).isNotBlank();
        assertThat(resp.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(resp.getDocumentIds()).hasSize(2);
        assertThat(resp.getMessage()).contains("2");
        assertThat(store.caseCount()).isEqualTo(1);
        assertThat(store.documentCount()).isEqualTo(2);
    }

    @Test
    void createCaseWithThreeFiles() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("a.docx", "b.docx", "c.docx"))
                .submittedBy("user")
                .build();

        CaseCreateResponse resp = caseService.createCase(req);
        assertThat(resp.getDocumentIds()).hasSize(3);
        assertThat(store.documentCount()).isEqualTo(3);
    }

    @Test
    void rejectsNullFilenames() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(null)
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要 2 份标书文件");
    }

    @Test
    void rejectsOneFile() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("only.docx"))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要 2 份标书文件");
    }

    @Test
    void rejectsNonDocxFile() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("a.docx", "b.pdf"))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 docx 格式文件");
    }

    @Test
    void rejectsBlankFilename() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("a.docx", "   "))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFilename() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(Arrays.asList("a.docx", null))
                .submittedBy("user")
                .build();

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filenameIsCaseInsensitiveDocxCheck() {
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("A.DOCX", "B.Docx"))
                .submittedBy("user")
                .build();

        CaseCreateResponse resp = caseService.createCase(req);
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
        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(List.of("a.docx", "b.docx"))
                .submittedBy("user")
                .build();
        CaseCreateResponse resp = caseService.createCase(req);

        assertThat(resp.getDocumentIds().get(0))
                .isNotEqualTo(resp.getDocumentIds().get(1));
    }

    @Test
    void nullRequestThrows() {
        assertThatThrownBy(() -> caseService.createCase(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
