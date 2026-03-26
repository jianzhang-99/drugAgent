package com.liang.drugagent.agent.preparation;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.scene.tender_review.TenderCaseStatus;
import com.liang.drugagent.scene.tender_review.model.*;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import com.liang.drugagent.scene.tender_review.support.storage.InMemoryTenderCaseStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 标书审查数据准备服务。
 *
 * <p>负责标书审查场景下的文件接入和结构化数据准备，是标书审查链路的前置环节。
 *
 * <p>核心职责：
 * <ul>
 *   <li>校验文件（类型、大小）</li>
 *   <li>创建审查任务（case）</li>
 *   <li>保存文件内容</li>
 *   <li>调用文档解析服务</li>
 *   <li>组装 TenderReviewData</li>
 * </ul>
 *
 * <p>调用链路：
 * <pre>
 * AgentSceneService -> TenderReviewPreparationService -> TenderReviewToolOrchestrator
 * </pre>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderReviewPreparationService {

    /** 支持的文件类型 */
    private static final List<String> SUPPORTED_FILE_TYPES = List.of(".docx", ".doc", ".md");

    /** 单文件最大大小（10MB） */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final InMemoryTenderCaseStore caseStore;
    private final TenderDocumentParseService documentParseService;

    /**
     * 准备标书审查数据。
     *
     * <p>完整流程：
     * <ol>
     *   <li>校验文件（类型、大小）</li>
     *   <li>创建审查任务（case）</li>
     *   <li>保存文件内容到存储</li>
     *   <li>调用文档解析服务解析每个文件</li>
     *   <li>组装 TenderReviewData 并返回</li>
     * </ol>
     *
     * @param context     Agent 上下文
     * @param files      上传的文件数组
     * @param submittedBy 提交人
     * @return 标书审查结构化数据
     * @throws IllegalArgumentException 文件校验失败时抛出
     * @throws RuntimeException 解析失败时抛出
     */
    public TenderReviewData prepareTenderReviewData(AgentChatContext context,
                                                    MultipartFile[] files,
                                                    String submittedBy) {
        String traceId = context != null ? context.getTraceId() : "unknown";
        log.info("[TenderReviewPreparationService] 开始准备标书审查数据, traceId={}, fileCount={}",
                traceId, files != null ? files.length : 0);

        // 1. 校验文件
        if (files == null || files.length == 0) {
            log.warn("[TenderReviewPreparationService] 文件列表为空");
            throw new IllegalArgumentException("请上传至少一个标书文件");
        }

        validateFiles(files);

        // 2. 创建 case
        String caseId = "CASE-" + UUID.randomUUID();
        TenderCase tenderCase = buildTenderCase(caseId, submittedBy, files);
        caseStore.saveCase(tenderCase);
        log.info("[TenderReviewPreparationService] 创建审查任务成功, caseId={}", caseId);

        // 3. 解析文件并组装数据
        List<TenderDocument> documents = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        List<Field> fields = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String documentId = "DOC-" + caseId + "-" + (i + 1);

            try {
                TenderDocumentParseResult parseResult = parseFile(documentId, file);

                // 保存文件字节
                caseStore.saveFileBytes(documentId, file.getBytes());

                // 构建文档元数据
                TenderDocument tenderDocument = buildTenderDocument(documentId, caseId, file);
                documents.add(tenderDocument);
                caseStore.saveDocument(tenderDocument);

                // 收集 blocks 和 fields
                if (parseResult.getParagraphBlocks() != null) {
                    blocks.addAll(parseResult.getParagraphBlocks());
                }
                if (parseResult.getTableBlocks() != null) {
                    blocks.addAll(parseResult.getTableBlocks());
                }
                if (parseResult.getFields() != null) {
                    fields.addAll(parseResult.getFields());
                }

                log.info("[TenderReviewPreparationService] 文件解析完成, documentId={}, filename={}, blocks={}, fields={}",
                        documentId, file.getOriginalFilename(),
                        parseResult.getParagraphCount() + parseResult.getTableCount(),
                        parseResult.getFieldCount());

            } catch (IOException e) {
                log.error("[TenderReviewPreparationService] 文件读取失败, documentId={}, filename={}",
                        documentId, file.getOriginalFilename(), e);
                throw new RuntimeException("文件读取失败: " + file.getOriginalFilename());
            }
        }

        // 4. 更新 case 的文档列表
        tenderCase.setDocumentIds(documents.stream()
                .map(TenderDocument::getDocumentId)
                .toList());
        tenderCase.setStatus(TenderCaseStatus.PARSED.name());
        tenderCase.setUpdatedAt(Instant.now());
        caseStore.saveCase(tenderCase);

        // 5. 构建比对范围
        List<CompareScope> compareScopes = buildCompareScopes(documents);

        // 6. 构建提取元信息
        ExtractionMeta extractionMeta = buildExtractionMeta();

        // 7. 组装 TenderReviewData
        TenderReviewData reviewData = TenderReviewData.builder()
                .aCase(tenderCase)
                .documents(documents)
                .blocks(blocks)
                .fields(fields)
                .compareScopes(compareScopes)
                .extractionMeta(extractionMeta)
                .build();

        log.info("[TenderReviewPreparationService] 标书审查数据准备完成, caseId={}, documents={}, blocks={}, fields={}",
                caseId, documents.size(), blocks.size(), fields.size());

        return reviewData;
    }

    /**
     * 校验文件列表。
     *
     * @param files 文件数组
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validateFiles(MultipartFile[] files) {
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("检测到空文件，请检查上传的文件");
            }

            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("文件名为空");
            }

            // 校验文件类型
            String lowerName = filename.toLowerCase();
            boolean supported = SUPPORTED_FILE_TYPES.stream()
                    .anyMatch(lowerName::endsWith);
            if (!supported) {
                throw new IllegalArgumentException("暂不支持的文件类型: " + filename + "，支持类型: " + SUPPORTED_FILE_TYPES);
            }

            // 校验文件大小
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件超过大小限制: " + filename + "，最大支持 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            }
        }

        log.debug("[TenderReviewPreparationService] 文件校验通过, fileCount={}", files.length);
    }

    /**
     * 构建审查任务对象。
     */
    private TenderCase buildTenderCase(String caseId, String submittedBy, MultipartFile[] files) {
        String displayName = files != null && files.length > 0 && files[0].getOriginalFilename() != null
                ? files[0].getOriginalFilename()
                : "未命名标书";

        return TenderCase.builder()
                .caseId(caseId)
                .scene("tender_review")
                .status(TenderCaseStatus.PENDING.name())
                .submittedBy(submittedBy != null ? submittedBy : "anonymous")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reviewResult("")
                .build();
    }

    /**
     * 解析单个文件。
     */
    private TenderDocumentParseResult parseFile(String documentId, MultipartFile file) throws IOException {
        try (var inputStream = file.getInputStream()) {
            return documentParseService.parseDocument(documentId, file.getOriginalFilename(), inputStream);
        } catch (IOException e) {
            log.error("[TenderReviewPreparationService] 文档解析异常, documentId={}, filename={}",
                    documentId, file.getOriginalFilename(), e);
            throw e;
        }
    }

    /**
     * 构建文档元数据。
     */
    private TenderDocument buildTenderDocument(String documentId, String caseId, MultipartFile file) {
        String filename = file.getOriginalFilename();
        String lowerName = filename != null ? filename.toLowerCase() : "";

        String fileType = "unknown";
        if (lowerName.endsWith(".docx")) {
            fileType = "docx";
        } else if (lowerName.endsWith(".doc")) {
            fileType = "doc";
        } else if (lowerName.endsWith(".md")) {
            fileType = "markdown";
        }

        return TenderDocument.builder()
                .documentId(documentId)
                .caseId(caseId)
                .documentName(filename)
                .filename(filename)
                .fileType(fileType)
                .status(TenderCaseStatus.PARSED.name())
                .build();
    }

    /**
     * 构建文档比对范围。
     *
     * <p>当文档数量 >= 2 时，创建一个全量标书比对范围。</p>
     */
    private List<CompareScope> buildCompareScopes(List<TenderDocument> documents) {
        if (documents == null || documents.size() < 2) {
            return new ArrayList<>();
        }

        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-" + UUID.randomUUID());
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(documents.stream()
                .map(TenderDocument::getDocumentId)
                .toList());

        return List.of(scope);
    }

    /**
     * 构建提取元信息。
     */
    private ExtractionMeta buildExtractionMeta() {
        return ExtractionMeta.builder()
                .schemaVersion("tender-review-v1")
                .parserVersion("tender-document-parse-v1")
                .parseSuccess(true)
                .build();
    }
}
