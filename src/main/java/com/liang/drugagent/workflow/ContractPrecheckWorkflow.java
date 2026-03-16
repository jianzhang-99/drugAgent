package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.EvidenceItem;
import com.liang.drugagent.domain.WorkflowResult;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合同预审工作流实现类。
 * 用于处理合同文件 AI 预审核场景，自动对用户提交的合同内容进行风险预筛查。
 *
 * @author liangjiajian
 */
@Component
public class ContractPrecheckWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public ContractPrecheckWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 合同预审场景类型
     */
    @Override
    public SceneEnum support() {
        return SceneEnum.CONTRACT_PRECHECK;
    }

    /**
     * 执行合同预审工作流逻辑。
     * 当前通过合同预审专用人设进行处理，后续将集成真实合同解析和规则库校验。
     *
     * @param context Agent 上下文，包含待审核文本和会话 ID
     * @return 包含预审意见、风险级别和审查证据的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "compliance_review",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneEnum.CONTRACT_PRECHECK, answer);
        result.setRiskLevel("MEDIUM");
        result.setSteps(List.of("场景识别", "合同预审问答"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本尚未接入真实合同解析，先走合同预审对话能力。", "system")
        ));
        return result;
    }
}
