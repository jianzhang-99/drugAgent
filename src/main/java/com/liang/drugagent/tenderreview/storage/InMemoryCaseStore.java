package com.liang.drugagent.tenderreview.storage;

import com.liang.drugagent.tenderreview.domain.Case;
import com.liang.drugagent.tenderreview.domain.CaseDocument;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCaseStore {

    private final ConcurrentHashMap<String, Case> cases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CaseDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> fileBytes = new ConcurrentHashMap<>();

    // ---- Case ----

    public void saveCase(Case c) {
        cases.put(c.getCaseId(), c);
    }

    public Optional<Case> findCase(String caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    // ---- CaseDocument ----

    public void saveDocument(CaseDocument doc) {
        documents.put(doc.getDocId(), doc);
    }

    public Optional<CaseDocument> findDocument(String docId) {
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
