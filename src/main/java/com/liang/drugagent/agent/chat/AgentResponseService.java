package com.liang.drugagent.agent.chat;

import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.ThinkingStep;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 响应服务实现。
 *
 * <p>将 AgentExecutionResult 转换为统一的 AgentChatResp 格式。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
public class AgentResponseService {

    public AgentChatResp buildResponse(AgentChatContext context, WorkflowRouteDecision decision, AgentExecutionResult executionResult) {
        log.info("[AgentResponseService] 开始构建响应: sessionId={}, success={}",
                context.getSessionId(), executionResult != null && executionResult.isSuccess());

        AgentChatResp resp = new AgentChatResp();

        // 填充元信息
        resp.setSessionId(context.getSessionId());
        resp.setTraceId(context.getTraceId());

        if (decision != null) {
            resp.setScene(decision.getScene() != null ? decision.getScene().name() : SceneEnum.UNKNOWN.name());
            resp.setRouteReason(decision.getReason());
            resp.setRouteSource(decision.getSource());
            resp.setConfidence(decision.getConfidence());
        } else {
            resp.setScene(SceneEnum.UNKNOWN.name());
            resp.setRouteReason("未知");
            resp.setRouteSource("default");
            resp.setConfidence(0.0);
        }

        // 填充执行结果
        if (executionResult == null) {
            return buildEmptyResponse(resp);
        }

        resp.setAnswer(executionResult.getAnswer());
        resp.setSummary(executionResult.getSummary());
        resp.setRiskLevel(executionResult.getRiskLevel());
        resp.setScore(executionResult.getScore() != null ? executionResult.getScore() : 0);
        resp.setCaseId(executionResult.getCaseId());
        resp.setDocumentIds(executionResult.getDocumentIds() != null ? executionResult.getDocumentIds() : new ArrayList<>());
        resp.setDocumentNames(executionResult.getDocumentNames() != null ? executionResult.getDocumentNames() : new ArrayList<>());
        resp.setReport(executionResult.getReport());
        resp.setEvidenceList(executionResult.getEvidenceList() != null ? executionResult.getEvidenceList() : new ArrayList<>());
        resp.setEvidenceGroups(executionResult.getEvidenceGroups() != null ? executionResult.getEvidenceGroups() : new ArrayList<>());
        resp.setSteps(executionResult.getSteps() != null ? executionResult.getSteps() : new ArrayList<>());
        resp.setSessionTitle(executionResult.getGeneratedTitle());
        resp.setThinkingSteps(buildThinkingSteps(decision, executionResult));
        fillStructuredFields(resp, executionResult);

        // 填充上传文件的ID列表
        if (context.getUploadedFiles() != null && !context.getUploadedFiles().isEmpty()) {
            List<String> fileIds = context.getUploadedFiles().stream()
                    .map(OssFile::getId)
                    .collect(Collectors.toList());
            resp.setFileIds(fileIds);
        }

        // 处理执行失败的情况
        if (!executionResult.isSuccess()) {
            resp.setAnswer("执行失败: " + executionResult.getErrorMessage());
            resp.setRequiresClarification(true);
            resp.setClarificationQuestion("系统执行遇到问题，请稍后重试或联系管理员。");
        }

        // 处理需要降级的情况
        if (executionResult.isNeedsFallback()) {
            resp.setRequiresClarification(true);
            // 优先使用执行结果中已有的澄清问题，否则使用默认提示
            String clarifyQuestion = executionResult.getClarificationQuestion();
            if (clarifyQuestion == null || clarifyQuestion.isBlank()) {
                clarifyQuestion = "系统检测到执行异常，可能需要人工介入处理。";
            }
            resp.setClarificationQuestion(clarifyQuestion);
        }

        log.info("[AgentResponseService] 响应构建完成: scene={}, answerLength={}",
                resp.getScene(), resp.getAnswer() != null ? resp.getAnswer().length() : 0);

        return resp;
    }

    /**
     * 构建空响应。
     */
    private AgentChatResp buildEmptyResponse(AgentChatResp resp) {
        resp.setAnswer("系统处理异常，未获取到有效结果");
        resp.setSummary("系统异常");
        resp.setRiskLevel("UNKNOWN");
        resp.setScore(0);
        resp.setRequiresClarification(true);
        resp.setClarificationQuestion("系统处理遇到问题，请稍后重试。");
        return resp;
    }

    private void fillStructuredFields(AgentChatResp resp, AgentExecutionResult executionResult) {
        if (executionResult.getReport() == null) {
            return;
        }

        List<String> managementSummary = executionResult.getReport().getManagementSummary();
        if (managementSummary != null && !managementSummary.isEmpty()) {
            resp.setManagementSummary(String.join("\n", managementSummary));
        }

        List<String> suggestedActions = executionResult.getReport().getRecommendedActions();
        if (suggestedActions != null && !suggestedActions.isEmpty()) {
            resp.setSuggestedActions(new ArrayList<>(suggestedActions));
        }

        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("caseId", executionResult.getReport().getCaseId());
        structuredData.put("overview", executionResult.getReport().getOverview());
        structuredData.put("riskItems", executionResult.getReport().getRiskItems());
        structuredData.put("managementSummary", executionResult.getReport().getManagementSummary());
        structuredData.put("recommendedActions", executionResult.getReport().getRecommendedActions());
        structuredData.put("explanations", executionResult.getReport().getExplanations());
        resp.setStructuredData(structuredData);
    }

    private List<ThinkingStep> buildThinkingSteps(WorkflowRouteDecision decision, AgentExecutionResult executionResult) {
        List<ThinkingStep> result = new ArrayList<>();
        int order = 1;

        if (decision != null) {
            result.add(ThinkingStep.builder()
                    .code("route")
                    .title("场景识别")
                    .detail(buildRouteDetail(decision))
                    .type("ROUTE")
                    .status("COMPLETED")
                    .order(order++)
                    .build());
        }

        List<ThinkingStep> executionThinkingSteps = executionResult.getThinkingSteps();
        if (executionThinkingSteps != null && !executionThinkingSteps.isEmpty()) {
            for (ThinkingStep step : executionThinkingSteps) {
                if (step == null) {
                    continue;
                }
                if ("route".equalsIgnoreCase(step.getCode())) {
                    continue;
                }
                result.add(ThinkingStep.builder()
                        .code(step.getCode())
                        .title(step.getTitle())
                        .detail(step.getDetail())
                        .type(step.getType())
                        .status(step.getStatus())
                        .order(order++)
                        .build());
            }
        } else {
            for (String stepName : normalizeExecutionSteps(executionResult.getSteps())) {
                result.add(ThinkingStep.builder()
                        .code(toStepCode(stepName))
                        .title(stepName)
                        .detail(resolveStepDetail(executionResult, stepName))
                        .type("EXECUTION")
                        .status("COMPLETED")
                        .order(order++)
                        .build());
            }
        }

        if (executionResult.isNeedsFallback() && executionResult.getClarificationQuestion() != null
                && !executionResult.getClarificationQuestion().isBlank()) {
            result.add(ThinkingStep.builder()
                    .code("clarification")
                    .title("补充信息确认")
                    .detail(executionResult.getClarificationQuestion())
                    .type("CLARIFICATION")
                    .status("INFO")
                    .order(order++)
                    .build());
        }

        if (!executionResult.isSuccess() && executionResult.getErrorMessage() != null
                && !executionResult.getErrorMessage().isBlank()) {
            result.add(ThinkingStep.builder()
                    .code("error")
                    .title("结果返回")
                    .detail(executionResult.getErrorMessage())
                    .type("ERROR")
                    .status("FAILED")
                    .order(order)
                    .build());
        }

        return result;
    }

    private List<String> normalizeExecutionSteps(List<String> rawSteps) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return List.of();
        }
        return rawSteps.stream()
                .filter(step -> step != null && !step.isBlank())
                .filter(step -> !"场景路由".equals(step))
                .toList();
    }

    private String buildRouteDetail(WorkflowRouteDecision decision) {
        String scene = decision.getScene() != null ? decision.getScene().name() : SceneEnum.UNKNOWN.name();
        String source = decision.getSource() != null ? decision.getSource() : "default";
        String reason = decision.getReason() != null ? decision.getReason() : "系统未返回明确路由原因";
        String confidence = decision.getConfidence() != null
                ? String.format("%.0f%%", decision.getConfidence() * 100)
                : "未知";
        return "已识别为 " + scene + " 场景，来源=" + source + "，置信度=" + confidence + "。"
                + "判断依据：" + reason;
    }

    private String resolveStepDetail(AgentExecutionResult executionResult, String stepName) {
        SceneEnum scene = executionResult.getScene();
        if (scene == SceneEnum.TENDER_REVIEW) {
            return switch (stepName) {
                case "数据校验" -> "检查是否已上传足够的标书文件，并确认文件可进入后续审查链路。";
                case "结构化加载" -> "解析标书文本与结构化字段，整理成可审查的统一数据对象。";
                case "规则命中分析" -> "先执行确定性规则，筛出明显的相似特征、异常线索和高风险命中项。";
                case "LLM语义分析" -> "对难以由规则直接判断的语义相似片段做补强分析。";
                case "误报豁免" -> "对模板引用、法规引用等可能的误报场景进行降权或豁免处理。";
                case "风险融合" -> "将规则命中、语义分析和豁免结果统一融合为最终风险等级与评分。";
                case "证据组装" -> "把命中规则对应的文本片段、来源位置和说明组织成证据链。";
                case "报告生成" -> "根据风险结论和证据链生成最终报告摘要与回答内容。";
                default -> stepName;
            };
        }

        return switch (stepName) {
            case "知识检索" -> "先检索知识库与历史上下文，判断是否需要使用外部证据增强回答。";
            case "RAG上下文构建" -> "将命中的知识片段整理为模型可直接使用的参考上下文。";
            case "LLM回答生成" -> "结合用户问题与补充上下文，生成最终回复。";
            case "问题理解" -> "识别用户当前问题、会话上下文以及回答目标。";
            case "回复生成" -> "根据问题理解结果生成最终回答。";
            default -> stepName;
        };
    }

    private String toStepCode(String stepName) {
        return switch (stepName) {
            case "数据校验" -> "validate_data";
            case "结构化加载" -> "prepare_data";
            case "规则命中分析" -> "rule_analysis";
            case "LLM语义分析" -> "semantic_analysis";
            case "误报豁免" -> "apply_exemption";
            case "风险融合" -> "risk_fusion";
            case "证据组装" -> "assemble_evidence";
            case "报告生成" -> "generate_report";
            case "知识检索" -> "knowledge_retrieval";
            case "RAG上下文构建" -> "build_rag_context";
            case "LLM回答生成" -> "generate_answer";
            case "问题理解" -> "understand_question";
            case "回复生成" -> "compose_answer";
            default -> "step_" + Integer.toHexString(stepName.hashCode());
        };
    }
}
