package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.enums.TenderCaseStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenderCaseService {

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
            document.setFileType("docx");
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
    }

    /**
     * 获取文件内容字节，不存在时返回 empty。
     */
    public Optional<byte[]> getFileContent(String docId) {
        return store.findFileBytes(docId);
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
            if (!name.toLowerCase().endsWith(".docx")) {
                throw new IllegalArgumentException("仅支持 docx 格式文件");
            }
        }
    }
}
