package com.liang.drugagent.agent;

import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.workflow.SceneWorkflow;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流注册表。
 *
 * 根据前置agent判断的结果通过场景来找到对应的workflow
 *
 * @author liangjiajian
 */
@Component
public class WorkflowRegistry {

    private final Map<SceneEnum, SceneWorkflow> workflowMap = new EnumMap<>(SceneEnum.class);

    public WorkflowRegistry(List<SceneWorkflow> workflows) {
        for (SceneWorkflow workflow : workflows) {
            workflowMap.put(workflow.support(), workflow);
        }
    }

    public SceneWorkflow get(SceneEnum sceneType) {
        // 任何未命中的场景都落到 UNKNOWN，避免空指针和不可控分支。
        return workflowMap.getOrDefault(sceneType, workflowMap.get(SceneEnum.UNKNOWN));
    }
}
