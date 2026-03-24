package com.liang.drugagent.scene.tender_review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.controller.domain.response.tender_review.TenderCaseCreateResp;
import com.liang.drugagent.scene.tender_review.TenderCaseStatus;
import com.liang.drugagent.scene.tender_review.model.*;
import com.liang.drugagent.scene.tender_review.support.TenderRuleEngine;
import com.liang.drugagent.scene.tender_review.support.storage.InMemoryTenderCaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 标书案例服务。
 *
 * <p>负责标书审查任务的完整生命周期管理，包括：
 * <ul>
 *   <li>任务创建与文档管理</li>
 *   <li>文件内容存储</li>
 *   <li>审查任务执行</li>
 *   <li>审查结果查询</li>
 * </ul>
 *
 * @author drug-agent
 */
@Service
public class TenderCaseService {

    private static final Logger log = LoggerFactory.getLogger(TenderCaseService.class);

    private final InMemoryTenderCaseStore store;
    private final ObjectMapper objectMapper;
    private final TenderRuleEngine ruleEngine;

    public TenderCaseService(InMemoryTenderCaseStore store, ObjectMapper objectMapper, TenderRuleEngine ruleEngine) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.ruleEngine = ruleEngine;
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

    /**
     * 根据文档ID获取文档信息。
     *
     * @param docId 文档ID
     * @return 文档信息（如果存在）
     */
    public Optional<TenderDocument> getDocument(String docId) {
        return store.findDocument(docId);
    }

    /**
     * 保存任务（用于更新任务状态）
     */
    public void saveCase(TenderCase tenderCase) {
        store.saveCase(tenderCase);
        log.info("Saved tender case: caseId={}, status={}", tenderCase.getCaseId(), tenderCase.getStatus());
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

    /**
     * 执行审查任务。
     *
     * @param caseId 任务ID
     * @param reviewData 审查数据
     * @return 审查结果
     */
    public TenderCase executeReview(String caseId, TenderReviewData reviewData) {
        log.info("Executing review for case: {}", caseId);

        Optional<TenderCase> caseOpt = store.findCase(caseId);
        if (caseOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到任务: " + caseId);
        }

        TenderCase tenderCase = caseOpt.get();
        tenderCase.setStatus(TenderCaseStatus.RUNNING.name());
        store.saveCase(tenderCase);

        // 调用规则引擎执行所有规则检查
        List<RuleHit> allHits = ruleEngine.execute(reviewData);

        // 计算综合评分和风险等级
        int totalScore = 0;
        String riskLevel = "LOW";

        for (RuleHit hit : allHits) {
            totalScore += hit.getWeight();
        }

        if (totalScore >= 80) {
            riskLevel = "HIGH";
        } else if (totalScore >= 50) {
            riskLevel = "MEDIUM";
        }

        tenderCase.setScore(totalScore);
        tenderCase.setRiskLevel(riskLevel);
        tenderCase.setStatus(TenderCaseStatus.COMPLETED.name());

        // 存储审查结果
        try {
            String resultJson = objectMapper.writeValueAsString(allHits);
            tenderCase.setReviewResult(resultJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize review result", e);
        }

        store.saveCase(tenderCase);
        log.info("Review completed for case: {}, score={}, riskLevel={}", caseId, totalScore, riskLevel);

        return tenderCase;
    }

    /**
     * 查询审查结果。
     *
     * @param caseId 任务ID
     * @return 审查结果
     */
    public Optional<TenderCase> getReviewResult(String caseId) {
        log.info("Getting review result for case: {}", caseId);
        return store.findCase(caseId);
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
