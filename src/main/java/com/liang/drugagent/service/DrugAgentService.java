package com.liang.drugagent.service;

import com.liang.drugagent.agent.*;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.workflow.SceneWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Drug Agent 主服务
 *
 * 所有前端请求从这里进来
 *
 * @author liangjiajian
 */
@Service
public class DrugAgentService {

    private final SceneRouter sceneRouter;
    private final WorkflowRegistry workflowRegistry;
    private final AgentChatService agentChatService;

    public DrugAgentService(SceneRouter sceneRouter, WorkflowRegistry workflowRegistry, AgentChatService agentChatService) {
        this.sceneRouter = sceneRouter;
        this.workflowRegistry = workflowRegistry;
        this.agentChatService = agentChatService;
    }

    public DrugAgentResp handle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        SceneEnum sceneType = sceneRouter.route(req, context);
        context.setSceneType(sceneType);

        // 每个场景只关心自己的执行细节，统一服务只负责调度，不承载业务判断。
        SceneWorkflow workflow = workflowRegistry.get(sceneType);
        WorkflowResult workflowResult = workflow.execute(context);

        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(workflowResult.getScene().name());
        resp.setRouteReason(String.valueOf(context.getAttributes().getOrDefault("routeReason", "unknown")));
        resp.setAnswer(workflowResult.getAnswer());
        resp.setRiskLevel(workflowResult.getRiskLevel());
        resp.setEvidenceList(workflowResult.getEvidenceList());
        resp.setSteps(workflowResult.getSteps());
        return resp;
    }

    public SseEmitter streamHandle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        SceneEnum sceneType = sceneRouter.route(req, context);
        context.setSceneType(sceneType);

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
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
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
                                emitter.complete();
                            } catch (IOException e) {
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
}
