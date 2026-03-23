package com.liang.drugagent.application.tender;

import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.service.tenderreview.TenderCaseService;
import com.liang.drugagent.service.tenderreview.TenderDocumentParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 标书审查应用服务。
 *
 * <p>处理标书审查核心业务流程编排。</p>
 *
 * @author liangjiajian
 */
@Service
public class TenderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TenderApplicationService.class);

    private final TenderCaseService caseService;
    private final TenderDocumentParseService documentParseService;

    public TenderApplicationService(TenderCaseService caseService,
                                   TenderDocumentParseService documentParseService) {
        this.caseService = caseService;
        this.documentParseService = documentParseService;
    }

    /**
     * 创建标书审查任务。
     */
    public TenderCaseCreateResp createCase(TenderCaseCreateReq req, List<String> documentIds) {
        return caseService.createCase(req);
    }

    /**
     * 解析文档。
     */
    public TenderDocumentParseResult parseDocument(String caseId, String docId) {
        Optional<byte[]> bytesOpt = caseService.getFileContent(docId);
        if (bytesOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到文件内容, docId=" + docId);
        }

        byte[] fileBytes = bytesOpt.get();
        if (fileBytes.length == 0) {
            throw new IllegalArgumentException("文档解析失败: 文件内容为空");
        }

        Optional<TenderDocument> documentOpt = caseService.getDocument(docId);
        if (documentOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到文档元数据, docId=" + docId);
        }

        String filename = documentOpt.get().getFilename();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(fileBytes)) {
            return documentParseService.parseDocument(docId, filename, stream);
        } catch (IOException e) {
            throw new IllegalStateException("文档解析失败: " + e.getMessage());
        }
    }

    /**
     * 执行审查任务。
     */
    public TenderCase executeReview(String caseId) {
        TenderReviewData reviewData = new TenderReviewData();
        return caseService.executeReview(caseId, reviewData);
    }

    /**
     * 获取审查结果。
     */
    public Optional<TenderCase> getReviewResult(String caseId) {
        return caseService.getReviewResult(caseId);
    }

    /**
     * 列出所有任务。
     */
    public List<TenderCase> listCases() {
        return caseService.listCases();
    }

    /**
     * 存储文件内容。
     */
    public void storeFileContent(String docId, byte[] content) {
        caseService.storeFileContent(docId, content);
    }
}
