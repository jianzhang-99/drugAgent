package com.liang.drugagent.scene.tender_review.preparation;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.tool.document.DocumentTool;
import com.liang.drugagent.tool.document.DocumentToolReq;
import com.liang.drugagent.tool.document.DocumentToolResult;
import com.liang.drugagent.tool.document.ParsedDocument;
import com.liang.drugagent.tool.document.TempDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 标书审查数据准备服务。
 *
 * <p>职责：
 * <ul>
 *   <li>从 {@link AgentChatContext} 和 {@link AgentChatReq} 中提取标书相关数据</li>
 *   <li>优先使用 metadata 中已存在的 tenderReviewData 或 documents</li>
 *   <li>若无 metadata 数据，尝试通过 fileIds 从 TenderCaseService 获取</li>
 *   <li>组装完整的 {@link TenderReviewData} 供下游使用</li>
 * </ul>
 *
 * <p>该服务属于场景层专属逻辑，不应放在通用层。
 *
 * @author liangjiajian
 * @see TenderReviewData
 * @see TenderReviewDataAssembler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderReviewPreparationService {

    private final TenderReviewDataAssembler dataAssembler;
    private final TenderCaseService caseService;
    private final DocumentTool documentTool;

    /**
     * 从请求上下文准备标书审查数据。
     *
     * <p>数据来源优先级：
     * <ol>
     *   <li>metadata 中已构建好的 tenderReviewData（直接使用）</li>
     *   <li>metadata 中包含 documents 内容和 fileIds（通过 assembler 解析）</li>
     *   <li>仅提供 fileIds（从 TenderCaseService 获取文件内容并解析）</li>
     * </ol>
     *
     * @param context Agent 上下文
     * @param req     对话请求
     * @return 标书审查数据（准备失败时返回 null）
     */
    public TenderReviewData prepare(AgentChatContext context, AgentChatReq req) {
        List<String> reqFileIds = req.getFileIds();
        List<String> ctxFileIds = context.getFileIds();
        String sessionScene = context.getSession() != null ? context.getSession().getLastScene() : "null";
        log.info("[TenderReviewPreparationService] 开始准备标书审查数据, sessionId={}, req.fileIds={}, context.fileIds={}, session.lastScene={}, hasUploadedFiles={}",
                context.getSessionId(), reqFileIds, ctxFileIds, sessionScene, hasUploadedFiles(req));

        // 优先处理上传的文件（本次请求中的临时文件）
        if (hasUploadedFiles(req)) {
            TenderReviewData fromUpload = buildFromUploadedFiles(context, req);
            if (fromUpload != null && hasEnoughDocuments(fromUpload)) {
                log.info("[TenderReviewPreparationService] 从上传文件构建数据成功, docCount={}",
                        fromUpload.getDocuments().size());
                return fromUpload;
            }
        }

        // 优先尝试从 metadata 解析
        TenderReviewData fromMetadata = dataAssembler.resolve(context);
        if (fromMetadata != null && hasEnoughDocuments(fromMetadata)) {
            log.info("[TenderReviewPreparationService] 从 metadata 解析到有效数据, docCount={}",
                    fromMetadata.getDocuments().size());
            enrichWithMetadata(fromMetadata, context, req);
            return fromMetadata;
        }

        // 尝试通过 fileIds 获取文档
        TenderReviewData fromFiles = buildFromFileIds(context, req);
        if (fromFiles != null && hasEnoughDocuments(fromFiles)) {
            log.info("[TenderReviewPreparationService] 从文件ID构建数据成功, docCount={}",
                    fromFiles.getDocuments().size());
            return fromFiles;
        }

        // 数据不足
        ensurePreparationError(context, req);
        log.warn("[TenderReviewPreparationService] 标书数据不足，无法进行审查");
        return null;
    }

    /**
     * 检查请求中是否有上传文件。
     */
    private boolean hasUploadedFiles(AgentChatReq req) {
        return req.getFiles() != null && req.getFiles().length >= 2;
    }

    /**
     * 从上传文件构建 TenderReviewData。
     *
     * <p>通过 DocumentTool 解析上传的文件，转换为 TenderReviewData。
     * 边界处理：文件数少于2份、文件为空、文件格式不支持、解析失败数>=2 时，
     * 会将具体错误原因存储到 context metadata 中供下游使用。</p>
     */
    private TenderReviewData buildFromUploadedFiles(AgentChatContext context, AgentChatReq req) {
        log.info("[TenderReviewPreparationService] 开始解析上传文件, fileCount={}", req.getFiles().length);

        int totalFileCount = req.getFiles().length;
        int emptyFileCount = 0;
        int unreadableFileCount = 0;

        // 将 MultipartFile[] 转换为 TempDocument[]
        List<TempDocument> tempDocuments = new ArrayList<>();
        for (int i = 0; i < req.getFiles().length; i++) {
            MultipartFile file = req.getFiles()[i];
            if (file == null || file.isEmpty()) {
                emptyFileCount++;
                log.info("[TenderReviewPreparationService] 检测到空文件: index={}, filename={}",
                        i, file != null ? file.getOriginalFilename() : "null");
                continue;
            }
            try {
                tempDocuments.add(TempDocument.builder()
                        .documentId("UPLOAD-" + i + "-" + System.currentTimeMillis())
                        .filename(file.getOriginalFilename())
                        .content(file.getBytes())
                        .build());
            } catch (Exception e) {
                unreadableFileCount++;
                log.warn("[TenderReviewPreparationService] 读取上传文件失败: filename={}, error={}",
                        file.getOriginalFilename(), e.getMessage());
            }
        }

        int validFileCount = tempDocuments.size();

        // 边界检查：文件数少于2份
        if (totalFileCount < 2) {
            String errorMsg = String.format("想帮你审查围标风险，但需要至少2份标书才能比对分析。本次只上传了%d份，请补充后再试。", totalFileCount);
            log.warn("[TenderReviewPreparationService] {}", errorMsg);
            context.getMetadata().put("preparationError", errorMsg);
            return null;
        }

        // 边界检查：所有文件都为空
        if (validFileCount == 0 && (emptyFileCount > 0 || unreadableFileCount > 0)) {
            String errorMsg;
            if (emptyFileCount == totalFileCount) {
                errorMsg = "所有上传的文件似乎都是空的，可以试着重新上传包含内容的标书文件。";
            } else {
                errorMsg = String.format("上传的文件似乎无法正常读取，请检查文件是否完整、格式是否受支持（支持Word、PDF等常见格式）。");
            }
            log.warn("[TenderReviewPreparationService] {}", errorMsg);
            context.getMetadata().put("preparationError", errorMsg);
            return null;
        }

        // 边界检查：有效文件不足2份
        if (validFileCount < 2) {
            String errorMsg = String.format("想帮你审查围标风险，但需要至少2份有效标书才能比对。本次有效文件：%d份（空文件：%d份，无法读取：%d份），请补充后再试。",
                    validFileCount, emptyFileCount, unreadableFileCount);
            log.warn("[TenderReviewPreparationService] {}", errorMsg);
            context.getMetadata().put("preparationError", errorMsg);
            return null;
        }

        // 调用 DocumentTool 解析
        DocumentToolReq docReq = DocumentToolReq.builder()
                .documents(tempDocuments)
                .build();

        DocumentToolResult parseResult = documentTool.parse(docReq);

        // 边界检查：解析失败数>=2 或 成功数<2
        if (parseResult.getSuccessCount() < 2) {
            String errorMsg = String.format("标书文件解析遇到问题，成功解析：%d份，解析失败：%d份。围标风险审查需要至少2份成功解析的标书，请检查文件格式是否受支持。",
                    parseResult.getSuccessCount(), parseResult.getFailureCount());
            if (parseResult.getErrors() != null && !parseResult.getErrors().isEmpty()) {
                log.warn("[TenderReviewPreparationService] 解析错误详情: {}", parseResult.getErrors());
            }
            log.warn("[TenderReviewPreparationService] {}", errorMsg);
            context.getMetadata().put("preparationError", errorMsg);
            return null;
        }

        // 转换为 ParsedDocument[]
        ParsedDocument[] parsedDocs = parseResult.getDocuments().toArray(new ParsedDocument[0]);

        // 通过 assembler 构建 TenderReviewData
        return dataAssembler.resolve(parsedDocs, context.getTraceId());
    }

    /**
     * 检查数据是否满足最低要求（至少 2 份文档）。
     */
    public boolean hasEnoughDocuments(TenderReviewData data) {
        return data != null
                && data.getDocuments() != null
                && data.getDocuments().size() >= 2;
    }

    /**
     * 从 fileIds 构建 TenderReviewData。
     *
     * <p>通过 sessionId 或 fileIds 从 TenderCaseService 获取文档内容，
     * 解析后组装为 TenderReviewData。</p>
     *
     * <p>关键设计：对于后续查询（如审查完成后的提问），fileIds 仍从请求传递，
     * 但需要从 OSS 重新获取文档内容并解析，确保审查流程可以正确执行。</p>
     */
    private TenderReviewData buildFromFileIds(AgentChatContext context, AgentChatReq req) {
        List<String> fileIds = resolveFileIds(context, req);
        if (fileIds == null || fileIds.size() < 2) {
            if (fileIds == null || fileIds.isEmpty()) {
                setPreparationError(context,
                        "还没检测到可审查的标书文件。想帮你审查围标风险，只需要上传至少2份标书就能开始。");
            } else {
                setPreparationError(context,
                        "目前只检测到 " + fileIds.size() + " 份标书文件，围标风险审查至少需要2份才能比对分析，请补充上传。");
            }
            log.info("[TenderReviewPreparationService] fileIds 不足，跳过文件构建方式");
            return null;
        }

        log.info("[TenderReviewPreparationService] 通过 fileIds 构建数据, fileIds={}", fileIds);

        // 构建 TempDocument 列表，从 OSS 获取文件内容并解析
        List<TempDocument> tempDocuments = new ArrayList<>();
        int fetchSuccessCount = 0;
        int fetchFailCount = 0;

        for (String fileId : fileIds) {
            Optional<byte[]> contentOpt = caseService.getFileContent(fileId);
            if (contentOpt.isPresent() && contentOpt.get().length > 0) {
                byte[] content = contentOpt.get();
                // 获取文档元信息用于构建文件名
                Optional<TenderDocument> docMetaOpt = caseService.getDocument(fileId);
                String filename = docMetaOpt.isPresent() ? docMetaOpt.get().getFilename() : fileId;
                String docId = docMetaOpt.isPresent() ? docMetaOpt.get().getDocumentId() : fileId;

                tempDocuments.add(TempDocument.builder()
                        .documentId(docId)
                        .filename(filename)
                        .content(content)
                        .build());
                fetchSuccessCount++;
                log.info("[TenderReviewPreparationService] 成功从 OSS 获取文档内容, fileId={}, filename={}, size={}",
                        fileId, filename, content.length);
            } else {
                fetchFailCount++;
                log.warn("[TenderReviewPreparationService] 从 OSS 获取文档内容失败, fileId={}", fileId);
            }
        }

        if (tempDocuments.size() < 2) {
            log.warn("[TenderReviewPreparationService] 从 OSS 获取的有效文档不足, success={}, fail={}",
                    fetchSuccessCount, fetchFailCount);
            return null;
        }

        // 调用 DocumentTool 解析文档
        DocumentToolReq docReq = DocumentToolReq.builder()
                .documents(tempDocuments)
                .build();
        DocumentToolResult parseResult = documentTool.parse(docReq);

        if (parseResult.getSuccessCount() < 2) {
            log.warn("[TenderReviewPreparationService] OSS 文档解析失败, successCount={}, failCount={}",
                    parseResult.getSuccessCount(), parseResult.getFailureCount());
            return null;
        }

        // 通过 dataAssembler 构建 TenderReviewData
        ParsedDocument[] parsedDocs = parseResult.getDocuments().toArray(new ParsedDocument[0]);
        return dataAssembler.resolve(parsedDocs, context.getTraceId());
    }

    /**
     * 从文档列表构建完整的 TenderReviewData。
     */
    private TenderReviewData buildTenderReviewDataFromDocuments(List<TenderDocument> documents,
                                                                AgentChatContext context) {
        TenderReviewData data = new TenderReviewData();

        // 设置 case
        var tenderCase = new com.liang.drugagent.scene.tender_review.model.TenderCase();
        tenderCase.setCaseId("CASE-" + UUID.randomUUID().toString().substring(0, 8));
        tenderCase.setScene("tender_review");
        data.setACase(tenderCase);

        data.setDocuments(documents);

        // 设置比对范围
        if (documents.size() >= 2) {
            var compareScope = new com.liang.drugagent.scene.tender_review.model.CompareScope();
            compareScope.setScopeId("CMP-" + UUID.randomUUID());
            compareScope.setScopeType("full_bid_compare");
            compareScope.setDocumentIds(documents.stream()
                    .map(TenderDocument::getDocumentId)
                    .toList());
            data.setCompareScopes(List.of(compareScope));
        }

        // 设置提取元信息
        var extractionMeta = new com.liang.drugagent.scene.tender_review.model.ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-v1");
        extractionMeta.setParserVersion("preparation-service-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);
        data.setExtractionMeta(extractionMeta);

        return data;
    }

    /**
     * 从请求中解析 fileIds。
     */
    private List<String> resolveFileIds(AgentChatContext context, AgentChatReq req) {
        // 优先使用 req 中的 fileIds（sendMessage 文字请求时 fileIds 在这里）
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            log.info("[TenderReviewPreparationService] resolveFileIds 命中 req.fileIds, size={}", req.getFileIds().size());
            return req.getFileIds();
        }

        // 尝试从 context 获取（submit 路径文件通过 uploadedFiles 进入）
        if (context.getFileIds() != null && !context.getFileIds().isEmpty()) {
            log.info("[TenderReviewPreparationService] resolveFileIds 命中 context.fileIds, size={}", context.getFileIds().size());
            return context.getFileIds();
        }

        // 尝试从 metadata 获取
        Map<String, Object> metadata = context.getMetadata();
        if (metadata != null && metadata.containsKey("fileIds")) {
            Object fileIdsObj = metadata.get("fileIds");
            if (fileIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> fileIds = (List<String>) fileIdsObj;
                log.info("[TenderReviewPreparationService] resolveFileIds 命中 metadata.fileIds, size={}", fileIds.size());
                return fileIds;
            }
        }

        // 优先级4（兜底）：从数据库按 sessionId 查找该会话上传过的历史文件
        // 解决刷新页面后 fileIds 丢失的问题
        String sessionId = context.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            List<TenderDocument> sessionDocs = caseService.findDocumentsBySessionId(sessionId);
            if (sessionDocs != null && !sessionDocs.isEmpty()) {
                List<String> dbFileIds = sessionDocs.stream()
                        .map(TenderDocument::getDocumentId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(java.util.stream.Collectors.toList());
                if (!dbFileIds.isEmpty()) {
                    log.info("[TenderReviewPreparationService] resolveFileIds 从数据库按 sessionId 恢复文件ID, sessionId={}, size={}", sessionId, dbFileIds.size());
                    return dbFileIds;
                }
            }
        }

        log.warn("[TenderReviewPreparationService] resolveFileIds 所有来源均为空，无法获取文件ID");
        return null;
    }

    private void ensurePreparationError(AgentChatContext context, AgentChatReq req) {
        Object existingError = context.getMetadata().get("preparationError");
        if (existingError instanceof String error && !error.isBlank()) {
            return;
        }

        List<String> fileIds = resolveFileIds(context, req);
        if (hasUploadedFiles(req)) {
            setPreparationError(context, "已收到上传请求，但有效标书文件不足2份。想帮你完成围标风险审查，请确保上传至少2份标书后再试。");
            return;
        }
        if (fileIds == null || fileIds.isEmpty()) {
            setPreparationError(context,
                    "还没检测到可审查的标书文件。想帮你审查围标风险，只需要上传至少2份标书就能开始。");
            return;
        }
        setPreparationError(context,
                "目前只检测到 " + fileIds.size() + " 份标书文件，围标风险审查至少需要2份才能比对分析，请补充上传。");
    }

    private void setPreparationError(AgentChatContext context, String errorMsg) {
        context.getMetadata().put("preparationError", errorMsg);
    }

    /**
     * 用 metadata 和 req 补充 TenderReviewData。
     */
    private void enrichWithMetadata(TenderReviewData data, AgentChatContext context, AgentChatReq req) {
        if (data.getACase() == null) {
            var tenderCase = new com.liang.drugagent.scene.tender_review.model.TenderCase();
            tenderCase.setCaseId(context.getTraceId());
            tenderCase.setScene("tender_review");
            data.setACase(tenderCase);
        }

        // 补充 reviewFocus
        Map<String, Object> metadata = context.getMetadata();
        if (metadata != null) {
            if (metadata.containsKey("reviewFocus")) {
                data.getACase().setScene((String) metadata.get("reviewFocus"));
            }
        }
    }

}
