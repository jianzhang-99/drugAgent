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
 * <p>Spring 会自动注入所有 SceneWorkflow 实现，
 * 这里再按 SceneEnum 建立映射，方便统一 Agent 快速分发。</p>
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
