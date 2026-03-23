package com.liang.drugagent.service;

import com.liang.drugagent.agent.*;
import com.liang.drugagent.agent.context.AgentContext;
import com.liang.drugagent.domain.entity.ChatSession;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.Block;
import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.ExtractionMeta;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.tenderreview.TenderCaseService;
import com.liang.drugagent.service.tenderreview.TenderDocumentParseService;
import com.liang.drugagent.workflow.SceneWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Drug Agent 主服务
 *
 * 所有前端请求从这里进来
 *
 * @author liangjiajian
 */
@Service
public class DrugAgentService {

    private static final Logger log = LoggerFactory.getLogger(DrugAgentService.class);

    private final UpperAgentOrchestrator upperAgentOrchestrator;
    private final AgentChatService agentChatService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    public DrugAgentService(UpperAgentOrchestrator upperAgentOrchestrator,
                            AgentChatService agentChatService,
                            TenderCaseService tenderCaseService,
                            TenderDocumentParseService tenderDocumentParseService,
                            ChatSessionService chatSessionService,
                            ChatMessageService chatMessageService) {
        this.upperAgentOrchestrator = upperAgentOrchestrator;
        this.agentChatService = agentChatService;
        this.tenderCaseService = tenderCaseService;
        this.tenderDocumentParseService = tenderDocumentParseService;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
    }

    public DrugAgentResp handle(DrugAgentReq req) {
        log.info("Start sync agent handling via UpperAgentOrchestrator");
        // Phase 1: 委托给 UpperAgentOrchestrator 统一处理
        DrugAgentResp resp = upperAgentOrchestrator.handle(req);
        log.info("Sync agent response ready: traceId={}, scene={}", resp.getTraceId(), resp.getScene());
        return resp;
    }

    public DrugAgentResp handleUploadedFiles(String query,
                                            String sceneHint,
                                            String sessionId,
                                            String userId,
                                            String submittedBy,
                                            MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少上传一个文件");
        }

        // 创建或获取 session
        ChatSession chatSession;
        if (sessionId == null || sessionId.isBlank()) {
            chatSession = chatSessionService.createSession("新对话", sceneHint, userId);
            sessionId = chatSession.getId();
        } else {
            chatSession = chatSessionService.getById(sessionId);
            if (chatSession == null) {
                chatSession = chatSessionService.createSession("新对话", sceneHint, userId);
                sessionId = chatSession.getId();
            }
        }

        // 保存用户消息
        String fileNamesJson = files.length > 0
            ? "[\"" + Arrays.stream(files).map(f -> Optional.ofNullable(f.getOriginalFilename()).orElse("unnamed")).collect(Collectors.joining("\",\"")) + "\"]"
            : null;
        chatMessageService.addMessage(sessionId, "user", query, fileNamesJson);

        DrugAgentReq req = new DrugAgentReq();
        req.setQuery(query);
        req.setSceneHint(sceneHint);
        req.setSessionId(sessionId);
        req.setUserId(userId);

        List<String> fileIds = new ArrayList<>();
        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            String generatedId = UUID.randomUUID().toString();
            fileIds.add(generatedId);
            uploadedFiles.add(Map.of(
                    "fileId", generatedId,
                    "filename", Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"),
                    "size", file.getSize()
            ));
        }
        req.setFileIds(fileIds);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("uploadedFiles", uploadedFiles);
        req.setMetadata(metadata);

        // Phase 1: 通过 UpperAgentOrchestrator 预判场景，用于决定是否需要解析文件
        // 后续 Phase 2 会将此逻辑收敛到 UpperAgentOrchestrator 内部
        var decision = upperAgentOrchestrator.decide(req, AgentContext.from(req));
        if (decision.getScene() == SceneEnum.TENDER_REVIEW) {
            hydrateTenderMetadata(req, submittedBy, files);
        }

        DrugAgentResp resp = handle(req);

        // 保存 AI 响应消息
        String aiContent = resp.getAnswer() != null ? resp.getAnswer() : resp.getSummary();
        String aiMetadata = String.format(
                "{\"scene\":\"%s\",\"riskLevel\":\"%s\",\"score\":%d,\"traceId\":\"%s\"}",
                resp.getScene() != null ? resp.getScene() : "",
                resp.getRiskLevel() != null ? resp.getRiskLevel() : "",
                resp.getScore(),
                resp.getTraceId() != null ? resp.getTraceId() : ""
        );
        chatMessageService.addMessage(sessionId, "assistant", aiContent, aiMetadata);

        // 更新 session 标题
        if (query != null && !query.isBlank() && chatSession.getTitle().equals("新对话")) {
            String title = query.length() > 20 ? query.substring(0, 20) + "..." : query;
            chatSessionService.updateSessionTitle(sessionId, title);
        }

        // 返回 sessionId 让前端同步会话
        resp.setSessionId(sessionId);
        return resp;
    }

    public SseEmitter streamHandle(DrugAgentReq req) {
        log.info("Start stream agent handling via UpperAgentOrchestrator");
        // Phase 1: 委托给 UpperAgentOrchestrator 统一处理流式请求
        return upperAgentOrchestrator.streamHandle(req);
    }

    private String buildSummary(WorkflowResult workflowResult) {
        if (workflowResult.getReport() != null
                && workflowResult.getReport().getOverview() != null
                && workflowResult.getReport().getOverview().getSummary() != null) {
            return workflowResult.getReport().getOverview().getSummary();
        }
        return workflowResult.getAnswer();
    }

    private void attachStructuredData(DrugAgentResp resp, AgentContext context, WorkflowResult workflowResult) {
        Object metadataCaseId = context.getMetadata().get("caseId");
        if (metadataCaseId != null) {
            resp.setCaseId(String.valueOf(metadataCaseId));
        }
        Object rawTenderData = context.getMetadata().get("tenderReviewData");
        if (rawTenderData instanceof TenderReviewData tenderReviewData && tenderReviewData.getDocuments() != null) {
            resp.setDocumentIds(tenderReviewData.getDocuments().stream()
                    .map(TenderDocument::getDocumentId)
                    .toList());
        }
        if (workflowResult.getReport() != null) {
            resp.setStructuredData(Map.of(
                    "reportType", "tender_review_report",
                    "report", workflowResult.getReport()
            ));
        }
    }

    private int getDocumentCount(AgentContext context, WorkflowResult workflowResult) {
        if (workflowResult.getEvidenceGroups() != null) {
            return workflowResult.getEvidenceGroups().stream()
                    .mapToInt(group -> group.getItems() != null ? group.getItems().size() : 0)
                    .sum();
        }
        return 0;
    }

    private List<String> buildManagementSummary(WorkflowResult workflowResult) {
        List<String> managementSummary = new ArrayList<>();
        if (workflowResult.getReport() != null && workflowResult.getReport().getOverview() != null) {
            managementSummary.add(workflowResult.getReport().getOverview().getSummary());
        }
        if (managementSummary.isEmpty()) {
            managementSummary.add("待处理");
        }
        return managementSummary;
    }

    private List<String> buildSuggestedActions(WorkflowResult workflowResult) {
        List<String> suggestedActions = new ArrayList<>();
        if (workflowResult.getRiskLevel() != null) {
            switch (workflowResult.getRiskLevel()) {
                case "HIGH":
                    suggestedActions.add("立即处理，进行详细审查");
                    suggestedActions.add("建议由专家团队复核");
                    break;
                case "MEDIUM":
                    suggestedActions.add("需要关注，定期复查");
                    suggestedActions.add("建议补充相关材料");
                    break;
                case "LOW":
                    suggestedActions.add("正常流程处理");
                    suggestedActions.add("建议保持定期监控");
                    break;
                default:
                    suggestedActions.add("按常规流程处理");
            }
        }
        return suggestedActions;
    }

    private List<String> normalizeSteps(List<String> steps) {
        if (steps == null) {
            return new ArrayList<>();
        }
        return steps.stream().map(this::translateStep).toList();
    }

    private List<EvidenceItem> normalizeEvidenceItems(List<EvidenceItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        List<EvidenceItem> normalized = new ArrayList<>();
        for (EvidenceItem item : items) {
            if (item == null) {
                continue;
            }
            EvidenceItem copy = new EvidenceItem();
            copy.setTitle(translateEvidenceTitle(item.getTitle()));
            copy.setContent(item.getContent());
            copy.setSource(item.getSource());
            normalized.add(copy);
        }
        return normalized;
    }

    private List<com.liang.drugagent.domain.workflow.EvidenceGroup> normalizeEvidenceGroups(
            List<com.liang.drugagent.domain.workflow.EvidenceGroup> groups) {
        if (groups == null) {
            return new ArrayList<>();
        }
        List<com.liang.drugagent.domain.workflow.EvidenceGroup> normalized = new ArrayList<>();
        for (com.liang.drugagent.domain.workflow.EvidenceGroup group : groups) {
            if (group == null) {
                continue;
            }
            com.liang.drugagent.domain.workflow.EvidenceGroup copy = new com.liang.drugagent.domain.workflow.EvidenceGroup();
            copy.setGroupKey(group.getGroupKey());
            copy.setTitle(translateEvidenceTitle(group.getTitle()));
            copy.setSummary(group.getSummary());
            copy.setSource(group.getSource());
            copy.setItems(normalizeEvidenceItems(group.getItems()));
            normalized.add(copy);
        }
        return normalized;
    }

    private String translateStep(String step) {
        if (step == null || step.isBlank()) {
            return "未命名步骤";
        }
        return switch (step) {
            case "scene_route" -> "场景路由";
            case "generic_review" -> "通用审查";
            case "structured_load" -> "结构化加载";
            case "rule_hit" -> "规则命中分析";
            case "false_positive_exemption" -> "误报豁免";
            case "risk_fusion" -> "风险融合";
            case "evidence_assembly" -> "证据组装";
            case "report_generation" -> "报告生成";
            default -> step;
        };
    }

    private String translateEvidenceTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未命名证据";
        }
        return switch (title) {
            case "fusion_score" -> "融合评分";
            case "rule_scan_result" -> "规则扫描结果";
            case "quote_gradient" -> "报价梯度异常";
            case "contact_nearby", "contact_proximity" -> "联系人近邻";
            case "team_overlap", "core_team_overlap" -> "核心团队重叠";
            case "proposal_copy", "proposal_plagiarism" -> "方案内容雷同";
            case "template_homology" -> "模板同源";
            case "rare_typo_cooccurrence" -> "罕见错误共现";
            case "error_replication" -> "错误复现";
            case "service_commitment" -> "服务承诺雷同";
            case "implementation_method" -> "实施方法雷同";
            case "case_data_plagiarism" -> "案例数据复用";
            case "risk_identification" -> "风险识别异常";
            default -> title;
        };
    }

    private void hydrateTenderMetadata(DrugAgentReq req, String submittedBy, MultipartFile[] files) {
        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            filenames.add(Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"));
        }

        TenderCaseCreateResp caseResp = tenderCaseService.createCase(TenderCaseCreateReq.builder()
                .filenames(filenames)
                .submittedBy(submittedBy)
                .build());

        List<TenderDocument> documents = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        ExtractionMeta extractionMeta = new ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-struct-v1");
        extractionMeta.setParserVersion("agent-upload-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String docId = caseResp.getDocumentIds().get(i);
            try {
                byte[] bytes = file.getBytes();
                tenderCaseService.storeFileContent(docId, bytes);
                TenderDocumentParseResult parseResult = tenderDocumentParseService.parseDocument(
                        docId,
                        filenames.get(i),
                        new ByteArrayInputStream(bytes)
                );
                documents.add(buildTenderDocument(caseResp.getCaseId(), docId, filenames.get(i)));
                blocks.addAll(Optional.ofNullable(parseResult.getParagraphBlocks()).orElse(List.of()));
                blocks.addAll(Optional.ofNullable(parseResult.getTableBlocks()).orElse(List.of()));
                fields.addAll(Optional.ofNullable(parseResult.getFields()).orElse(List.of()));
                if (parseResult.getExtractionMeta() != null && Boolean.FALSE.equals(parseResult.getExtractionMeta().getParseSuccess())) {
                    extractionMeta.setParseSuccess(Boolean.FALSE);
                }
            } catch (IOException e) {
                throw new IllegalStateException("文件处理失败: " + filenames.get(i), e);
            }
        }

        TenderReviewData data = new TenderReviewData();
        data.setACase(buildTenderCase(caseResp.getCaseId(), submittedBy, caseResp.getDocumentIds()));
        data.setDocuments(documents);
        data.setBlocks(blocks);
        data.setFields(fields);
        data.setCompareScopes(buildCompareScopes(caseResp.getDocumentIds()));
        data.setExtractionMeta(extractionMeta);

        Map<String, Object> metadata = new HashMap<>(Optional.ofNullable(req.getMetadata()).orElse(Map.of()));
        metadata.put("caseId", caseResp.getCaseId());
        metadata.put("tenderReviewData", data);
        req.setMetadata(metadata);
    }

    private TenderCase buildTenderCase(String caseId, String submittedBy, List<String> documentIds) {
        TenderCase tenderCase = new TenderCase();
        tenderCase.setCaseId(caseId);
        tenderCase.setScene("tender_review");
        tenderCase.setStatus("PARSED");
        tenderCase.setSubmittedBy(submittedBy);
        tenderCase.setCreatedAt(Instant.now());
        tenderCase.setDocumentIds(documentIds);
        return tenderCase;
    }

    private TenderDocument buildTenderDocument(String caseId, String docId, String filename) {
        TenderDocument document = new TenderDocument();
        document.setCaseId(caseId);
        document.setDocumentId(docId);
        document.setFilename(filename);
        document.setDocumentName(filename);
        document.setStatus("PARSED");
        document.setFileType(resolveFileType(filename));
        return document;
    }

    private List<CompareScope> buildCompareScopes(List<String> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            return List.of();
        }
        CompareScope compareScope = new CompareScope();
        compareScope.setScopeId("CMP-" + UUID.randomUUID());
        compareScope.setScopeType("full_bid_compare");
        compareScope.setDocumentIds(documentIds);
        return List.of(compareScope);
    }

    private String resolveFileType(String filename) {
        if (filename == null) {
            return "unknown";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".doc")) {
            return "doc";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        return "unknown";
    }
}
