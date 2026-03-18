package com.liang.drugagent.tenderreview.service;

import com.liang.drugagent.tenderreview.domain.Case;
import com.liang.drugagent.tenderreview.domain.CaseCreateRequest;
import com.liang.drugagent.tenderreview.domain.CaseCreateResponse;
import com.liang.drugagent.tenderreview.domain.CaseDocument;
import com.liang.drugagent.tenderreview.domain.enums.CaseStatus;
import com.liang.drugagent.tenderreview.storage.InMemoryCaseStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CaseService {

    private final InMemoryCaseStore store;

    public CaseService(InMemoryCaseStore store) {
        this.store = store;
    }

    /**
     * 校验请求并创建任务主记录及文档记录。
     *
     * @throws IllegalArgumentException 校验失败时抛出，含中文错误描述
     */
    public CaseCreateResponse createCase(CaseCreateRequest req) {
        validateRequest(req);

        String caseId = UUID.randomUUID().toString();
        List<String> documentIds = new ArrayList<>();

        List<CaseDocument> docs = new ArrayList<>();
        for (String filename : req.getFilenames()) {
            String docId = UUID.randomUUID().toString();
            documentIds.add(docId);
            docs.add(CaseDocument.builder()
                    .docId(docId)
                    .caseId(caseId)
                    .filename(filename)
                    .status(CaseStatus.PENDING)
                    .build());
        }

        Case c = Case.builder()
                .caseId(caseId)
                .status(CaseStatus.PENDING)
                .submittedBy(req.getSubmittedBy())
                .createdAt(Instant.now())
                .documentIds(documentIds)
                .build();

        store.saveCase(c);
        docs.forEach(store::saveDocument);

        return CaseCreateResponse.builder()
                .caseId(caseId)
                .status(CaseStatus.PENDING)
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

    private void validateRequest(CaseCreateRequest req) {
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
