package com.liang.drugagent.service;

import com.liang.drugagent.agent.*;
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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    private final SceneRouter sceneRouter;
    private final WorkflowRegistry workflowRegistry;
    private final AgentChatService agentChatService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    public DrugAgentService(SceneRouter sceneRouter,
                            WorkflowRegistry workflowRegistry,
                            AgentChatService agentChatService,
                            TenderCaseService tenderCaseService,
                            TenderDocumentParseService tenderDocumentParseService) {
        this.sceneRouter = sceneRouter;
        this.workflowRegistry = workflowRegistry;
        this.agentChatService = agentChatService;
        this.tenderCaseService = tenderCaseService;
        this.tenderDocumentParseService = tenderDocumentParseService;
    }

    public DrugAgentResp handle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        log.info("Start sync agent handling: traceId={}, sessionId={}, userId={}",
                context.getTraceId(), context.getSessionId(), context.getUserId());
        SceneEnum sceneType = sceneRouter.route(req, context);
        context.setSceneType(sceneType);
        log.info("Scene routed: traceId={}, scene={}, routeReason={}",
                context.getTraceId(),
                sceneType,
                context.getAttributes().getOrDefault("routeReason", "unknown"));

        // 每个场景只关心自己的执行细节，统一服务只负责调度，不承载业务判断。
        SceneWorkflow workflow = workflowRegistry.get(sceneType);
        WorkflowResult workflowResult = workflow.execute(context);
        log.info("Workflow executed: traceId={}, scene={}, riskLevel={}, stepCount={}",
                context.getTraceId(),
                workflowResult.getScene(),
                workflowResult.getRiskLevel(),
                workflowResult.getSteps() == null ? 0 : workflowResult.getSteps().size());

        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(workflowResult.getScene().name());
        resp.setRouteReason(String.valueOf(context.getAttributes().getOrDefault("routeReason", "unknown")));
        resp.setSummary(buildSummary(workflowResult));
        resp.setAnswer(workflowResult.getAnswer());
        resp.setRiskLevel(workflowResult.getRiskLevel());
        resp.setReport(workflowResult.getReport());
        resp.setEvidenceList(workflowResult.getEvidenceList());
        resp.setEvidenceGroups(workflowResult.getEvidenceGroups());
        resp.setSteps(workflowResult.getSteps());
        attachStructuredData(resp, context, workflowResult);
        log.info("Sync agent response ready: traceId={}, scene={}", context.getTraceId(), resp.getScene());
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

        SceneEnum scene = sceneRouter.route(req, AgentContext.from(req));
        if (scene == SceneEnum.TENDER_REVIEW) {
            hydrateTenderMetadata(req, submittedBy, files);
        }

        return handle(req);
    }

    public SseEmitter streamHandle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        log.info("Start stream agent handling: traceId={}, sessionId={}, userId={}",
                context.getTraceId(), context.getSessionId(), context.getUserId());
        SceneEnum sceneType = sceneRouter.route(req, context);
        context.setSceneType(sceneType);
        log.info("Stream scene routed: traceId={}, scene={}, routeReason={}",
                context.getTraceId(),
                sceneType,
                context.getAttributes().getOrDefault("routeReason", "unknown"));

        SseEmitter emitter = new SseEmitter(0L);
        try {
            sendEvent(emitter, "meta", Map.of(
                    "traceId", context.getTraceId(),
                    "scene", sceneType.name(),
                    "routeReason", String.valueOf(context.getAttributes().getOrDefault("routeReason", "unknown"))
            ));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        if (sceneType == SceneEnum.UNKNOWN) {
            try {
                sendEvent(emitter, "delta", "当前请求场景还不够明确。你可以补充是标书查重、合同预审，还是药品与耗材风险预警。");
                sendEvent(emitter, "done", buildDonePayload(sceneType));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        agentChatService.streamChatWithScene(
                        req.getQuery(),
                        resolveAgentType(sceneType),
                        context.getSessionId()
                )
                .subscribe(
                        chunk -> {
                            try {
                                sendEvent(emitter, "delta", chunk);
                            } catch (IOException e) {
                                log.error("Send SSE delta failed: traceId={}, scene={}", context.getTraceId(), sceneType, e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("Stream agent handling failed: traceId={}, scene={}", context.getTraceId(), sceneType, error);
                            try {
                                sendEvent(emitter, "error", error.getMessage());
                            } catch (IOException ignored) {
                                // Ignore secondary send failure when connection is already broken.
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                sendEvent(emitter, "done", buildDonePayload(sceneType));
                                log.info("Stream agent handling completed: traceId={}, scene={}", context.getTraceId(), sceneType);
                                emitter.complete();
                            } catch (IOException e) {
                                log.error("Send SSE done failed: traceId={}, scene={}", context.getTraceId(), sceneType, e);
                                emitter.completeWithError(e);
                            }
                        }
                );

        return emitter;
    }

    private String resolveAgentType(SceneEnum sceneType) {
        if (sceneType == SceneEnum.CONTRACT_PRECHECK) {
            return "compliance_review";
        }
        if (sceneType == SceneEnum.RISK_ALERT) {
            return "data_analysis";
        }
        return "default";
    }

    private Map<String, Object> buildDonePayload(SceneEnum sceneType) {
        WorkflowResult template = WorkflowResult.of(sceneType, "");
        if (sceneType == SceneEnum.TENDER_REVIEW) {
            template.setRiskLevel("NONE");
            template.setSteps(List.of("场景识别", "标书查重分析"));
            template.setEvidenceList(List.of(
                    new EvidenceItem("执行说明", "当前流式版本先复用基础问答能力，后续接入标书比对与语义查重能力。", "system")
            ));
        } else if (sceneType == SceneEnum.CONTRACT_PRECHECK) {
            template.setRiskLevel("MEDIUM");
            template.setSteps(List.of("场景识别", "合同预审问答"));
            template.setEvidenceList(List.of(
                    new EvidenceItem("执行说明", "当前流式版本尚未接入真实合同解析，先走合同预审对话能力。", "system")
            ));
        } else if (sceneType == SceneEnum.RISK_ALERT) {
            template.setRiskLevel("PENDING");
            template.setSteps(List.of("场景识别", "风险预警分析"));
            template.setEvidenceList(List.of(
                    new EvidenceItem("执行说明", "当前流式版本先复用数据分析人设，后续再接真实预警分析服务。", "system")
            ));
        } else {
            template.setRiskLevel("UNKNOWN");
            template.setSteps(List.of("场景识别", "兜底回复"));
            template.setEvidenceList(List.of(
                    new EvidenceItem("路由说明", "未命中明确场景，进入兜底工作流。", "system")
            ));
        }
        return Map.of(
                "riskLevel", template.getRiskLevel(),
                "steps", template.getSteps(),
                "evidenceList", template.getEvidenceList()
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
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
