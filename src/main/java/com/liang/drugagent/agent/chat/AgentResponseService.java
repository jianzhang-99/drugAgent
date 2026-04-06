package com.liang.drugagent.agent.chat;

import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
            resp.setClarificationQuestion("系统检测到执行异常，可能需要人工介入处理。");
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
}
