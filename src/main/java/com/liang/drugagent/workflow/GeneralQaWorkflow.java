package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.resp.EvidenceItem;
import com.liang.drugagent.domain.resp.WorkflowResult;
import com.liang.drugagent.service.AgentChatService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeneralQaWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;

    public GeneralQaWorkflow(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @Override
    public SceneType support() {
        return SceneType.GENERAL_QA;
    }

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
