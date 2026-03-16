package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.EvidenceItem;
import com.liang.drugagent.domain.WorkflowResult;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合规审查工作流实现类。
 * 用于处理医药合规审查场景，自动对用户提交的内容进行法规符合性检查。
 *
 * @author liangjiajian
 */
@Component
public class ComplianceReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public ComplianceReviewWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 合规审查场景类型
     */
    @Override
    public SceneType support() {
        return SceneType.COMPLIANCE_REVIEW;
    }

    /**
     * 执行合规审查工作流逻辑。
     * 目前通过合规审查专用人设进行处理，后续将集成真实的文件解析和法规库检索。
     *
     * @param context Agent 上下文，包含待审查文本和会话 ID
     * @return 包含合规性判断、风险级别和审查证据的统一结果对象
     */
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
