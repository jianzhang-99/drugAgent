package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.EvidenceItem;
import com.liang.drugagent.domain.WorkflowResult;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 药品分析工作流实现类。
 * 用于处理药品数据分析相关的场景，通过分析药品统计数据并生成监管建议。
 *
 * @author liangjiajian
 */
@Component
public class DrugAnalysisWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public DrugAnalysisWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 药品分析场景类型
     */
    @Override
    public SceneType support() {
        return SceneType.DRUG_ANALYSIS;
    }

    /**
     * 执行药品分析工作流逻辑。
     * 当前 MVP 版本主要依赖大模型的人设对话能力进行模拟分析。
     *
     * @param context Agent 上下文，包含查询参数和会话 ID
     * @return 包含药品分析结果、风险评级和执行步骤的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        // 当前先走数据分析场景的人设对话，后续这里会切换为：
        // 查询统计数据 -> 结构化分析 -> 生成监管建议。
        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "data_analysis",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneType.DRUG_ANALYSIS, answer);
        result.setRiskLevel("PENDING");
        result.setSteps(List.of("场景识别", "药品分析问答"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本先复用数据分析人设，后续再接真实统计服务。", "system")
        ));
        return result;
    }
}
