package com.liang.drugagent.scene;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.shared.model.WorkflowResult;

/**
 * 场景工作流接口。
 *
 * @author liangjiajian
 */
public interface SceneWorkflow {

    SceneEnum support();

    WorkflowResult execute(AgentChatContext context);
}
