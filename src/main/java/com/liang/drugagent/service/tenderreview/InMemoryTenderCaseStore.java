package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTenderCaseStore {

    private final ConcurrentHashMap<String, TenderCase> cases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TenderDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> fileBytes = new ConcurrentHashMap<>();

    // ---- Case ----

    public void saveCase(TenderCase c) {
        cases.put(c.getCaseId(), c);
    }

    public Optional<TenderCase> findCase(String caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    // ---- CaseDocument ----

    public void saveDocument(TenderDocument doc) {
        documents.put(doc.getDocumentId(), doc);
    }

    public Optional<TenderDocument> findDocument(String docId) {
        return Optional.ofNullable(documents.get(docId));
    }

    // ---- File Bytes ----

    public void saveFileBytes(String docId, byte[] bytes) {
        fileBytes.put(docId, bytes);
    }

    public Optional<byte[]> findFileBytes(String docId) {
        return Optional.ofNullable(fileBytes.get(docId));
    }

    // ---- Test helpers ----

    public int caseCount() {
        return cases.size();
    }

    public int documentCount() {
        return documents.size();
    }
}
