package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.resp.EvidenceItem;
import com.liang.drugagent.domain.resp.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底工作流实现类。
 * 当系统无法识别用户意图或未匹配到特定场景时，由该工作流提供友好的引导回复。
 *
 * @author liangjiajian
 */
@Component
public class FallbackWorkflow implements SceneWorkflow {

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 未知场景类型
     */
    @Override
    public SceneType support() {
        return SceneType.UNKNOWN;
    }

    /**
     * 执行兜底逻辑。
     * 向用户说明当前无法识别意图，并列出支持的功能场景以进行引导。
     *
     * @param context Agent 上下文，包含原始查询内容
     * @return 包含引导语的统一结果对象
     */
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
