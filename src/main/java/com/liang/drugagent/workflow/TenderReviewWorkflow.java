package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.EvidenceItem;
import com.liang.drugagent.domain.WorkflowResult;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标书审查工作流实现类。
 * 用于处理标书雷同与语义查重场景。
 *
 * @author liangjiajian
 */
@Component
public class TenderReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public TenderReviewWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 标书审查场景类型
     */
    @Override
    public SceneEnum support() {
        return SceneEnum.TENDER_REVIEW;
    }

    /**
     * 执行标书审查工作流逻辑。
     * 当前先复用基础对话能力，后续接入标书比对、语义查重和证据汇总能力。
     *
     * @param context Agent 上下文，包含用户问题和会话 ID
     * @return 包含审查内容、执行步骤和证据的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "default",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, answer);
        result.setRiskLevel("NONE");
        result.setSteps(List.of("场景识别", "标书查重分析"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本先复用基础问答能力，后续接入标书比对与语义查重能力。", "system")
        ));
        return result;
    }
}
