package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.enums.TenderCaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTenderCaseStoreTest {

    private InMemoryTenderCaseStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTenderCaseStore();
    }

    @Test
    void saveCaseAndFind() {
        TenderCase c = new TenderCase();
        c.setCaseId("case-1");
        c.setStatus(TenderCaseStatus.PENDING.name());
        c.setSubmittedBy("tester");
        c.setCreatedAt(Instant.now());
        c.setDocumentIds(List.of("doc-1", "doc-2"));
        store.saveCase(c);

        Optional<TenderCase> found = store.findCase("case-1");
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
        TenderDocument doc = new TenderDocument();
        doc.setDocumentId("doc-1");
        doc.setCaseId("case-1");
        doc.setFilename("tender_a.docx");
        doc.setStatus(TenderCaseStatus.PENDING.name());
        store.saveDocument(doc);

        Optional<TenderDocument> found = store.findDocument("doc-1");
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

        TenderCase caseRecord = new TenderCase();
        caseRecord.setCaseId("c1");
        caseRecord.setStatus(TenderCaseStatus.PENDING.name());
        caseRecord.setSubmittedBy("u");
        caseRecord.setCreatedAt(Instant.now());
        caseRecord.setDocumentIds(List.of());
        store.saveCase(caseRecord);

        TenderDocument doc1 = new TenderDocument();
        doc1.setDocumentId("d1");
        doc1.setCaseId("c1");
        doc1.setFilename("a.docx");
        doc1.setStatus(TenderCaseStatus.PENDING.name());
        store.saveDocument(doc1);

        TenderDocument doc2 = new TenderDocument();
        doc2.setDocumentId("d2");
        doc2.setCaseId("c1");
        doc2.setFilename("b.docx");
        doc2.setStatus(TenderCaseStatus.PENDING.name());
        store.saveDocument(doc2);

        assertThat(store.caseCount()).isEqualTo(1);
        assertThat(store.documentCount()).isEqualTo(2);
    }

    @Test
    void saveCaseOverwritesExisting() {
        TenderCase c1 = new TenderCase();
        c1.setCaseId("c1");
        c1.setStatus(TenderCaseStatus.PENDING.name());
        c1.setSubmittedBy("u1");
        c1.setCreatedAt(Instant.now());
        c1.setDocumentIds(List.of());

        TenderCase c2 = new TenderCase();
        c2.setCaseId("c1");
        c2.setStatus(TenderCaseStatus.PARSED.name());
        c2.setSubmittedBy("u2");
        c2.setCreatedAt(Instant.now());
        c2.setDocumentIds(List.of());

        store.saveCase(c1);
        store.saveCase(c2);

        assertThat(store.findCase("c1").get().getStatus()).isEqualTo(TenderCaseStatus.PARSED.name());
        assertThat(store.caseCount()).isEqualTo(1);
    }
}
