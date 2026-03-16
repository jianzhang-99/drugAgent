package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.EvidenceItem;
import com.liang.drugagent.domain.WorkflowResult;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用法规问答工作流实现类。
 * 用于处理日常法规、政策咨询等通用问答场景。
 *
 * @author liangjiajian
 */
@Component
public class GeneralQaWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public GeneralQaWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 获取当前工作流支持的场景类型。
     *
     * @return 通用问答场景类型
     */
    @Override
    public SceneType support() {
        return SceneType.GENERAL_QA;
    }

    /**
     * 执行通用问答工作流逻辑。
     * 当前复用基础对话能力，未来可接入 RAG 知识库检索以提高准确性。
     *
     * @param context Agent 上下文，包含用户问题和会话 ID
     * @return 包含回答内容、执行步骤和证据的统一结果对象
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        // 通用法规问答场景目前直接复用基础对话能力，
        // 后续可以在这里接入知识库检索结果，增强回答可追溯性。
        String answer = agentChatService.chatWithScene(
                context.getQuery(),
                "default",
                context.getSessionId()
        );
        WorkflowResult result = WorkflowResult.of(SceneType.GENERAL_QA, answer);
        result.setRiskLevel("NONE");
        result.setSteps(List.of("场景识别", "法规问答生成"));
        result.setEvidenceList(List.of(
                new EvidenceItem("执行说明", "当前 MVP 版本先复用基础问答能力。", "system")
        ));
        return result;
    }
}
