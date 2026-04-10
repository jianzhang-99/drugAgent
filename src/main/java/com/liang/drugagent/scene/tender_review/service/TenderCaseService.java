package com.liang.drugagent.scene.tender_review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.common.entity.TaskCard;
import com.liang.drugagent.agent.common.mapper.TaskCardMapper;
import com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.controller.domain.response.tender_review.TenderCaseCreateResp;
import com.liang.drugagent.scene.tender_review.TenderCaseStatus;
import com.liang.drugagent.scene.tender_review.entity.TenderCaseDocumentEntity;
import com.liang.drugagent.scene.tender_review.mapper.TenderCaseDocumentMapper;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.support.TenderRuleEngine;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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
@Slf4j
@Service
public class TenderCaseService {

    private static final String TASK_TYPE_TENDER_REVIEW = "TENDER_REVIEW";
    private static final String SCENE_TENDER_REVIEW = "tender_review";

    private final TaskCardMapper taskCardMapper;
    private final TenderCaseDocumentMapper tenderCaseDocumentMapper;
    private final OssFileMapper ossFileMapper;
    private final TencentCosStorageService cosStorageService;
    private final ObjectMapper objectMapper;
    private final TenderRuleEngine ruleEngine;

    public TenderCaseService(TaskCardMapper taskCardMapper,
                             TenderCaseDocumentMapper tenderCaseDocumentMapper,
                             OssFileMapper ossFileMapper,
                             TencentCosStorageService cosStorageService,
                             ObjectMapper objectMapper,
                             TenderRuleEngine ruleEngine) {
        this.taskCardMapper = taskCardMapper;
        this.tenderCaseDocumentMapper = tenderCaseDocumentMapper;
        this.ossFileMapper = ossFileMapper;
        this.cosStorageService = cosStorageService;
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
        log.info("创建标书案例: 提交人={}, 文件名={}", req.getSubmittedBy(), req.getFilenames());

        String caseId = UUID.randomUUID().toString();
        List<String> documentIds = new ArrayList<>();

        List<TenderCaseDocumentEntity> docs = new ArrayList<>();
        for (String filename : req.getFilenames()) {
            String docId = UUID.randomUUID().toString();
            documentIds.add(docId);
            TenderCaseDocumentEntity document = TenderCaseDocumentEntity.builder()
                    .id(docId)
                    .caseId(caseId)
                    .fileName(filename)
                    .documentName(filename)
                    .fileType(resolveFileType(filename))
                    .status(TenderCaseStatus.PENDING.name())
                    .build();
            docs.add(document);
        }

        TaskCard taskCard = TaskCard.builder()
                .id(caseId)
                .caseId(caseId)
                .taskName(buildTaskName(req.getFilenames()))
                .taskType(TASK_TYPE_TENDER_REVIEW)
                .scene(SCENE_TENDER_REVIEW)
                .status(TenderCaseStatus.PENDING.name())
                .progress(0)
                .riskLevel("UNKNOWN")
                .submittedBy(req.getSubmittedBy())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        taskCardMapper.insert(taskCard);
        docs.forEach(tenderCaseDocumentMapper::insert);
        log.info("标书案例已持久化: caseId={}, 文档数量={}", caseId, docs.size());

        return TenderCaseCreateResp.builder()
                .caseId(caseId)
                .status(TenderCaseStatus.PENDING)
                .documentIds(documentIds)
                .message("任务创建成功，待解析文档数：" + documentIds.size())
                .build();
    }

    /**
     * 存储文件内容（字节）到 OSS，并更新文档记录。
     */
    public void storeFileContent(String docId, byte[] bytes) {
        TenderCaseDocumentEntity document = tenderCaseDocumentMapper.selectById(docId);
        if (document == null) {
            throw new IllegalArgumentException("未找到标书文档: " + docId);
        }
        OssFile ossFile = cosStorageService.saveBytesFile(document.getCaseId(), document.getFileName(), bytes);
        document.setOssFileId(ossFile.getId());
        document.setStatus(TenderCaseStatus.PENDING.name());
        tenderCaseDocumentMapper.updateById(document);
        log.info("已存储标书文件内容到 OSS: docId={}, ossFileId={}, 大小={}",
                docId, ossFile.getId(), bytes == null ? 0 : bytes.length);
    }

    /**
     * 获取文件内容字节，不存在时返回 empty。
     */
    public Optional<byte[]> getFileContent(String docId) {
        Optional<OssFile> ossFileOpt = resolveOssFile(docId);
        if (ossFileOpt.isEmpty()) {
            return Optional.empty();
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("tender_case_", ".tmp");
            cosStorageService.downloadFile(ossFileOpt.get().getOssUrl(), tempFile);
            return Optional.of(Files.readAllBytes(tempFile.toPath()));
        } catch (Exception e) {
            log.error("读取标书文件内容失败: docId={}, error={}", docId, e.getMessage(), e);
            return Optional.empty();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 根据文档ID获取文档信息。
     *
     * @param docId 文档ID
     * @return 文档信息（如果存在）
     */
    public Optional<TenderDocument> getDocument(String docId) {
        TenderCaseDocumentEntity entity = tenderCaseDocumentMapper.selectById(docId);
        if (entity != null) {
            return Optional.of(toTenderDocument(entity));
        }

        OssFile ossFile = ossFileMapper.selectById(docId);
        if (ossFile == null || ossFile.getUploadStatus() == null || ossFile.getUploadStatus() != 1) {
            return Optional.empty();
        }

        return Optional.of(TenderDocument.builder()
                .caseId(ossFile.getSessionId())
                .documentId(ossFile.getId())
                .documentName(ossFile.getFileName())
                .filename(ossFile.getFileName())
                .fileType(ossFile.getFileSuffix())
                .status(TenderCaseStatus.PENDING.name())
                .build());
    }

    /**
     * 保存任务（用于更新任务状态）
     */
    public void saveCase(TenderCase tenderCase) {
        TaskCard existing = taskCardMapper.selectById(tenderCase.getCaseId());
        if (existing == null) {
            TaskCard taskCard = toTaskCard(tenderCase);
            taskCardMapper.insert(taskCard);
        } else {
            TaskCard update = toTaskCard(tenderCase);
            taskCardMapper.updateById(update);
        }
        log.info("已保存标书案例: caseId={}, 状态={}", tenderCase.getCaseId(), tenderCase.getStatus());
    }

    /**
     * 查询所有任务，按创建时间倒序返回。
     */
    public List<TenderCase> listCases() {
        LambdaQueryWrapper<TaskCard> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskCard::getTaskType, TASK_TYPE_TENDER_REVIEW)
                .eq(TaskCard::getIsDeleted, 0);

        List<TenderCase> cases = taskCardMapper.selectList(queryWrapper).stream()
                .map(this::toTenderCase)
                .sorted(Comparator.comparing(TenderCase::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        log.info("查询标书案例列表: 数量={}", cases.size());
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
        log.info("执行标书案例审查: caseId={}", caseId);

        Optional<TenderCase> caseOpt = findCase(caseId);
        if (caseOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到任务: " + caseId);
        }

        TenderCase tenderCase = caseOpt.get();
        tenderCase.setStatus(TenderCaseStatus.RUNNING.name());
        saveCase(tenderCase);

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
            log.error("序列化审查结果失败", e);
        }

        saveCase(tenderCase);
        log.info("标书案例审查完成: caseId={}, 评分={}, 风险等级={}", caseId, totalScore, riskLevel);

        return tenderCase;
    }

    /**
     * 查询审查结果。
     *
     * @param caseId 任务ID
     * @return 审查结果
     */
    public Optional<TenderCase> getReviewResult(String caseId) {
        log.info("获取审查结果: caseId={}", caseId);
        return findCase(caseId);
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

    public List<TenderDocument> findDocumentsByCaseId(String caseId) {
        LambdaQueryWrapper<TenderCaseDocumentEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TenderCaseDocumentEntity::getCaseId, caseId)
                .orderByAsc(TenderCaseDocumentEntity::getCreatedAt);
        return tenderCaseDocumentMapper.selectList(queryWrapper).stream()
                .map(this::toTenderDocument)
                .toList();
    }

    public List<TenderDocument> findDocumentsBySessionId(String sessionId) {
        LambdaQueryWrapper<OssFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OssFile::getSessionId, sessionId)
                .eq(OssFile::getUploadStatus, 1)
                .orderByAsc(OssFile::getCreatedAt);
        return ossFileMapper.selectList(queryWrapper).stream()
                .map(ossFile -> TenderDocument.builder()
                        .caseId(ossFile.getSessionId())
                        .documentId(ossFile.getId())
                        .documentName(ossFile.getFileName())
                        .filename(ossFile.getFileName())
                        .fileType(ossFile.getFileSuffix())
                        .status(TenderCaseStatus.PENDING.name())
                        .build())
                .toList();
    }

    private Optional<TenderCase> findCase(String caseId) {
        TaskCard taskCard = taskCardMapper.selectById(caseId);
        if (taskCard == null || taskCard.getIsDeleted() != null && taskCard.getIsDeleted() == 1) {
            return Optional.empty();
        }
        return Optional.of(toTenderCase(taskCard));
    }

    private TenderCase toTenderCase(TaskCard taskCard) {
        List<String> documentIds = findDocumentsByCaseId(taskCard.getCaseId()).stream()
                .map(TenderDocument::getDocumentId)
                .toList();
        return TenderCase.builder()
                .caseId(taskCard.getCaseId())
                .scene(taskCard.getScene())
                .status(taskCard.getStatus())
                .submittedBy(taskCard.getSubmittedBy())
                .createdAt(toInstant(taskCard.getCreatedAt()))
                .updatedAt(toInstant(taskCard.getUpdatedAt()))
                .documentIds(documentIds)
                .riskLevel(taskCard.getRiskLevel())
                .score(taskCard.getScore())
                .reviewResult(taskCard.getSummary())
                .build();
    }

    private TaskCard toTaskCard(TenderCase tenderCase) {
        return TaskCard.builder()
                .id(tenderCase.getCaseId())
                .caseId(tenderCase.getCaseId())
                .taskName("标书审查-" + tenderCase.getCaseId())
                .taskType(TASK_TYPE_TENDER_REVIEW)
                .scene(tenderCase.getScene() != null ? tenderCase.getScene() : SCENE_TENDER_REVIEW)
                .status(tenderCase.getStatus())
                .riskLevel(tenderCase.getRiskLevel())
                .submittedBy(tenderCase.getSubmittedBy())
                .score(tenderCase.getScore())
                .summary(tenderCase.getReviewResult())
                .createdAt(toLocalDateTime(tenderCase.getCreatedAt()))
                .updatedAt(LocalDateTime.now())
                .startedAt(TenderCaseStatus.RUNNING.name().equals(tenderCase.getStatus()) ? LocalDateTime.now() : null)
                .completedAt(TenderCaseStatus.COMPLETED.name().equals(tenderCase.getStatus()) ? LocalDateTime.now() : null)
                .build();
    }

    private TenderDocument toTenderDocument(TenderCaseDocumentEntity entity) {
        return TenderDocument.builder()
                .caseId(entity.getCaseId())
                .documentId(entity.getId())
                .documentName(entity.getDocumentName())
                .filename(entity.getFileName())
                .fileType(entity.getFileType())
                .status(entity.getStatus())
                .build();
    }

    private Optional<OssFile> resolveOssFile(String docId) {
        // 优先尝试从 tender_case_document 查找（可能表不存在，需容错）
        try {
            TenderCaseDocumentEntity document = tenderCaseDocumentMapper.selectById(docId);
            if (document != null && document.getOssFileId() != null && !document.getOssFileId().isBlank()) {
                return Optional.ofNullable(ossFileMapper.selectById(document.getOssFileId()));
            }
        } catch (Exception e) {
            log.warn("[TenderCaseService] tender_case_document 表查询失败，回退到 oss_file 表直接查询, docId={}, error={}",
                    docId, e.getMessage());
        }

        // 回退：直接用 docId 查 oss_file 表
        OssFile direct = ossFileMapper.selectById(docId);
        return Optional.ofNullable(direct);
    }

    private String buildTaskName(List<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return "标书审查任务";
        }
        return "标书审查-" + filenames.get(0);
    }

    private Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
