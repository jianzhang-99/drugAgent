package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.resp.EvidenceItem;
import com.liang.drugagent.domain.resp.WorkflowResult;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author liangjiajian
 */
@Component
public class ComplianceReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public ComplianceReviewWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @Override
    public SceneType support() {
        return SceneType.COMPLIANCE_REVIEW;
    }

    @Override
    public WorkflowResult execute(AgentContext context) {
        // MVP 先复用现有合规审查人设，等文件解析和法规检索能力稳定后，
        // 再把这里替换成“文件提取 -> 法规检索 -> 合规判断”的固定链路。
        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "compliance_review",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneType.COMPLIANCE_REVIEW, answer);
        result.setRiskLevel("MEDIUM");
        result.setSteps(List.of("场景识别", "合规审查问答"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本尚未接入真实文件解析，先走合规审查对话能力。", "system")
        ));
        return result;
    }
}
