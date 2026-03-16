package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.resp.EvidenceItem;
import com.liang.drugagent.domain.resp.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FallbackWorkflow implements SceneWorkflow {

    @Override
    public SceneType support() {
        return SceneType.UNKNOWN;
    }

    @Override
    public WorkflowResult execute(AgentContext context) {
        // 兜底工作流的职责不是“硬答”，而是明确告诉前端或用户：
        // 当前还需要更清晰的业务意图，避免产生误导性输出。
        WorkflowResult result = WorkflowResult.of(
                SceneType.UNKNOWN,
                "当前请求场景还不够明确。你可以补充是法规问答、合规审查，还是药品数据分析。"
        );
        result.setRiskLevel("UNKNOWN");
        result.setSteps(List.of("场景识别", "兜底回复"));
        result.setEvidenceList(List.of(
                new EvidenceItem("路由说明", "未命中明确场景，进入兜底工作流。", "system")
        ));
        return result;
    }
}
