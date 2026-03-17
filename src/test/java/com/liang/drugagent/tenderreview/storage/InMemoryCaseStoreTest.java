package com.liang.drugagent.tenderreview.storage;

import com.liang.drugagent.tenderreview.domain.Case;
import com.liang.drugagent.tenderreview.domain.CaseDocument;
import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCaseStoreTest {

    private InMemoryCaseStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCaseStore();
    }

    @Test
    void saveCaseAndFind() {
        Case c = Case.builder()
                .caseId("case-1")
                .status(CaseStatus.PENDING)
                .submittedBy("tester")
                .createdAt(Instant.now())
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        store.saveCase(c);

        Optional<Case> found = store.findCase("case-1");
        assertThat(found).isPresent();
        assertThat(found.get().getSubmittedBy()).isEqualTo("tester");
        assertThat(found.get().getDocumentIds()).containsExactly("doc-1", "doc-2");
    }

    @Test
    void findCaseReturnsEmptyWhenAbsent() {
        assertThat(store.findCase("no-such-case")).isEmpty();
    }

    @Test
    void saveDocumentAndFind() {
        CaseDocument doc = CaseDocument.builder()
                .docId("doc-1")
                .caseId("case-1")
                .filename("tender_a.docx")
                .status(CaseStatus.PENDING)
                .build();
        store.saveDocument(doc);

        Optional<CaseDocument> found = store.findDocument("doc-1");
        assertThat(found).isPresent();
        assertThat(found.get().getFilename()).isEqualTo("tender_a.docx");
    }

    @Test
    void findDocumentReturnsEmptyWhenAbsent() {
        assertThat(store.findDocument("no-such-doc")).isEmpty();
    }

    @Test
    void saveFileBytesAndFind() {
        byte[] data = new byte[]{1, 2, 3};
        store.saveFileBytes("doc-1", data);

        Optional<byte[]> found = store.findFileBytes("doc-1");
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(data);
    }

    @Test
    void findFileBytesReturnsEmptyWhenAbsent() {
        assertThat(store.findFileBytes("no-such-doc")).isEmpty();
    }

    @Test
    void caseCountAndDocumentCount() {
        assertThat(store.caseCount()).isZero();
        assertThat(store.documentCount()).isZero();

        store.saveCase(Case.builder().caseId("c1").status(CaseStatus.PENDING)
                .submittedBy("u").createdAt(Instant.now()).documentIds(List.of()).build());
        store.saveDocument(CaseDocument.builder().docId("d1").caseId("c1")
                .filename("a.docx").status(CaseStatus.PENDING).build());
        store.saveDocument(CaseDocument.builder().docId("d2").caseId("c1")
                .filename("b.docx").status(CaseStatus.PENDING).build());

        assertThat(store.caseCount()).isEqualTo(1);
        assertThat(store.documentCount()).isEqualTo(2);
    }

    @Test
    void saveCaseOverwritesExisting() {
        Case c1 = Case.builder().caseId("c1").status(CaseStatus.PENDING)
                .submittedBy("u1").createdAt(Instant.now()).documentIds(List.of()).build();
        Case c2 = Case.builder().caseId("c1").status(CaseStatus.PARSED)
                .submittedBy("u2").createdAt(Instant.now()).documentIds(List.of()).build();

        store.saveCase(c1);
        store.saveCase(c2);

        assertThat(store.findCase("c1").get().getStatus()).isEqualTo(CaseStatus.PARSED);
        assertThat(store.caseCount()).isEqualTo(1);
    }
}
