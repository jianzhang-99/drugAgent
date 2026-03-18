package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.enums.TenderCaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenderCaseService {

    private static final Logger log = LoggerFactory.getLogger(TenderCaseService.class);

    private final InMemoryTenderCaseStore store;

    public TenderCaseService(InMemoryTenderCaseStore store) {
        this.store = store;
    }

    /**
     * 校验请求并创建任务主记录及文档记录。
     *
     * @throws IllegalArgumentException 校验失败时抛出，含中文错误描述
     */
    public TenderCaseCreateResp createCase(TenderCaseCreateReq req) {
        validateRequest(req);
        log.info("Creating tender case: submittedBy={}, filenames={}", req.getSubmittedBy(), req.getFilenames());

        String caseId = UUID.randomUUID().toString();
        List<String> documentIds = new ArrayList<>();

        List<TenderDocument> docs = new ArrayList<>();
        for (String filename : req.getFilenames()) {
            String docId = UUID.randomUUID().toString();
            documentIds.add(docId);
            TenderDocument document = new TenderDocument();
            document.setDocumentId(docId);
            document.setCaseId(caseId);
            document.setFilename(filename);
            document.setDocumentName(filename);
            document.setFileType(resolveFileType(filename));
            document.setStatus(TenderCaseStatus.PENDING.name());
            docs.add(document);
        }

        TenderCase c = new TenderCase();
        c.setCaseId(caseId);
        c.setScene("tender_review");
        c.setStatus(TenderCaseStatus.PENDING.name());
        c.setSubmittedBy(req.getSubmittedBy());
        c.setCreatedAt(Instant.now());
        c.setDocumentIds(documentIds);

        store.saveCase(c);
        docs.forEach(store::saveDocument);
        log.info("Tender case persisted: caseId={}, documentCount={}", caseId, docs.size());

        return TenderCaseCreateResp.builder()
                .caseId(caseId)
                .status(TenderCaseStatus.PENDING)
                .documentIds(documentIds)
                .message("任务创建成功，待解析文档数：" + documentIds.size())
                .build();
    }

    /**
     * 存储文件内容（字节）到 store。
     */
    public void storeFileContent(String docId, byte[] bytes) {
        store.saveFileBytes(docId, bytes);
        log.info("Stored tender file content: docId={}, size={}", docId, bytes == null ? 0 : bytes.length);
    }

    /**
     * 获取文件内容字节，不存在时返回 empty。
     */
    public Optional<byte[]> getFileContent(String docId) {
        return store.findFileBytes(docId);
    }

    public Optional<TenderDocument> getDocument(String docId) {
        return store.findDocument(docId);
    }

    /**
     * 查询所有任务，按创建时间倒序返回。
     */
    public List<TenderCase> listCases() {
        List<TenderCase> cases = store.findAllCases().stream()
                .sorted(Comparator.comparing(TenderCase::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        log.info("Listed tender cases: count={}", cases.size());
        return cases;
    }

    // ---- internal ----

    private void validateRequest(TenderCaseCreateReq req) {
        if (req == null || req.getFilenames() == null || req.getFilenames().size() < 2) {
            throw new IllegalArgumentException("至少需要 2 份标书文件进行比对审查");
        }
        for (String name : req.getFilenames()) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!(lowerName.endsWith(".docx") || lowerName.endsWith(".doc") || lowerName.endsWith(".md"))) {
                throw new IllegalArgumentException("仅支持 doc、docx、md 格式文件");
            }
        }
    }

    private String resolveFileType(String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) return "docx";
        if (lowerName.endsWith(".doc")) return "doc";
        if (lowerName.endsWith(".md")) return "md";
        return "unknown";
    }
}
