package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.workflow.RagOutcome;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.rag.WorkflowRagAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险预警工作流实现类。
 * 用于处理医疗耗材与药品合规风险预警场景，通过分析指标和异常信号生成监管建议。
 *
 * @author liangjiajian
 */
@Component
public class RiskAlertWorkflow implements SceneWorkflow {

    private final WorkflowRagAdapter workflowRagAdapter;

    public RiskAlertWorkflow(WorkflowRagAdapter workflowRagAdapter) {
        this.workflowRagAdapter = workflowRagAdapter;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 风险预警场景类型
     */
    @Override
    public SceneEnum support() {
        return SceneEnum.RISK_ALERT;
    }

    /**
     * 执行风险预警工作流逻辑。
     * 当前 MVP 版本主要依赖大模型的人设对话能力进行模拟分析。
     *
     * @param context Agent 上下文，包含查询参数和会话 ID
     * @return 包含风险分析结果、风险评级和执行步骤的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        RagOutcome outcome = workflowRagAdapter.ask(context, SceneEnum.RISK_ALERT);

        WorkflowResult result = WorkflowResult.of(SceneEnum.RISK_ALERT, outcome.getAnswer());
        result.setRiskLevel(outcome.getRiskLevel() == null ? "PENDING" : outcome.getRiskLevel());
        result.setSteps(List.of("场景识别", "混合检索", "RAG门控", "风险预警输出"));
        result.setEvidenceList(outcome.getEvidenceList());
        return result;
    }
}
