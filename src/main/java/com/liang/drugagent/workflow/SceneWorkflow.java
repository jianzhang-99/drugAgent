package com.liang.drugagent.workflow;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.WorkflowResult;

/**
 * 场景工作流接口。
 * 定义了不同业务场景（如药品分析、合规审查等）下 Agent 的标准化执行链路。
 *
 * @author liangjiajian
 */
public interface SceneWorkflow {

    /**
     * 获取当前工作流支持的业务场景类型。
     *
     * @return 场景类型 Enum
     */
    SceneType support();

    /**
     * 执行业务场景逻辑。
     * 每个工作流实现类需负责具体的数据采集、分析、检索或对话分发逻辑。
     *
     * @param context Agent 上下文环境
     * @return 包含执行结果、证据及风险评级的统一响应对象
     */
    WorkflowResult execute(AgentContext context);
}
