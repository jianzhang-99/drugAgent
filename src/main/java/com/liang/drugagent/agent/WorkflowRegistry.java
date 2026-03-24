package com.liang.drugagent.agent;

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

    /**
     * 根据场景类型获取对应的工作流。
     *
     * <p>如果未找到对应场景的工作流，则返回 UNKNOWN 场景的降级工作流，
     * 确保不会返回 null。</p>
     *
     * @param sceneType 场景类型（非空）
     * @return 对应场景的工作流实例，不会为 null
     */
    public SceneWorkflow get(SceneEnum sceneType) {
        // 任何未命中的场景都落到 UNKNOWN，避免空指针和不可控分支。
        return workflowMap.getOrDefault(sceneType, workflowMap.get(SceneEnum.UNKNOWN));
    }
}
