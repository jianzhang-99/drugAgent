package com.liang.drugagent.domain;

import com.liang.drugagent.workflow.SceneType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流执行结果。
 *
 * <p>该对象用于服务内部在不同工作流之间传递统一结果，
 * 不直接暴露为前端的 API 返回体。</p>
 *
 * @author liangjiajian
 */
@Setter
@Getter
public class WorkflowResult {

    private SceneType scene;
    private String answer;
    private String riskLevel;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<String> steps = new ArrayList<>();

    public static WorkflowResult of(SceneType scene, String answer) {
        WorkflowResult result = new WorkflowResult();
        result.setScene(scene);
        result.setAnswer(answer);
        return result;
    }

}
