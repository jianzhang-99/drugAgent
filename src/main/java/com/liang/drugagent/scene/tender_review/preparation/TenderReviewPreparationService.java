package com.liang.drugagent.scene.tender_review.preparation;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
        log.info("[TenderReviewPreparationService] 开始准备标书审查数据, sessionId={}",
                context.getSessionId());

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
        log.warn("[TenderReviewPreparationService] 标书数据不足，无法进行审查");
        return null;
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
     */
    private TenderReviewData buildFromFileIds(AgentChatContext context, AgentChatReq req) {
        List<String> fileIds = resolveFileIds(context, req);
        if (fileIds == null || fileIds.size() < 2) {
            log.info("[TenderReviewPreparationService] fileIds 不足，跳过文件构建方式");
            return null;
        }

        log.info("[TenderReviewPreparationService] 通过 fileIds 构建数据, fileIds={}", fileIds);

        // 尝试从 session 获取该会话关联的文档
        List<TenderDocument> documents = fetchDocumentsBySessionId(context.getSessionId());

        // 如果 session 没有足够的文档，尝试直接用 fileIds 获取
        if (documents.size() < 2) {
            for (String fileId : fileIds) {
                Optional<TenderDocument> docOpt = caseService.getDocument(fileId);
                if (docOpt.isPresent() && !containsDoc(documents, fileId)) {
                    documents.add(docOpt.get());
                }
            }
        }

        if (documents.size() < 2) {
            log.warn("[TenderReviewPreparationService] 可用文档不足, docCount={}", documents.size());
            return null;
        }

        return buildTenderReviewDataFromDocuments(documents, context);
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
     * 根据 sessionId 获取该会话关联的所有文档。
     */
    private List<TenderDocument> fetchDocumentsBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        List<com.liang.drugagent.scene.tender_review.model.TenderCase> allCases = caseService.listCases();
        java.util.List<TenderDocument> documents = new java.util.ArrayList<>();

        for (var tenderCase : allCases) {
            if (sessionId.equals(tenderCase.getCaseId()) && tenderCase.getDocumentIds() != null) {
                for (String docId : tenderCase.getDocumentIds()) {
                    caseService.getDocument(docId).ifPresent(documents::add);
                }
            }
        }

        return documents;
    }

    /**
     * 从请求中解析 fileIds。
     */
    private List<String> resolveFileIds(AgentChatContext context, AgentChatReq req) {
        // 优先使用 context 中的 fileIds
        if (context.getFileIds() != null && !context.getFileIds().isEmpty()) {
            return context.getFileIds();
        }

        // 尝试从 metadata 获取
        Map<String, Object> metadata = context.getMetadata();
        if (metadata != null && metadata.containsKey("fileIds")) {
            Object fileIdsObj = metadata.get("fileIds");
            if (fileIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> fileIds = (List<String>) fileIdsObj;
                return fileIds;
            }
        }

        // 尝试从 req 获取
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            return req.getFileIds();
        }

        return null;
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

    private boolean containsDoc(List<TenderDocument> documents, String docId) {
        return documents.stream().anyMatch(d -> d.getDocumentId().equals(docId));
    }
}
