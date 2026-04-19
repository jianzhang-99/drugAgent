package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentResponseServiceTest {

    private final AgentResponseService service = new AgentResponseService();

    @Test
    void shouldPropagateReasoningContentIntoAgentResponse() {
        AgentChatReq req = AgentChatReq.builder()
                .sessionId("session-1")
                .query("test query")
                .build();
        AgentChatContext context = AgentChatContext.from(req, "session-1");

        WorkflowRouteDecision decision = WorkflowRouteDecision.builder()
                .scene(SceneEnum.DEFAULT)
                .reason("default route")
                .source("test")
                .confidence(1.0)
                .requiresClarification(false)
                .build();

        AgentExecutionResult executionResult = AgentExecutionResult.builder()
                .success(true)
                .scene(SceneEnum.DEFAULT)
                .answer("final answer")
                .reasoningContent("reasoning content")
                .summary("summary")
                .build();

        AgentChatResp resp = service.buildResponse(context, decision, executionResult);

        assertNotNull(resp);
        assertEquals("final answer", resp.getAnswer());
        assertEquals("reasoning content", resp.getReasoningContent());
        assertEquals(SceneEnum.DEFAULT.name(), resp.getScene());
    }
}
