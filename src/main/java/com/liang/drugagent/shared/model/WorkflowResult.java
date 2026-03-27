package com.liang.drugagent.shared.model;

import com.liang.drugagent.scene.SceneEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResult {

    private SceneEnum scene;
    private String answer;
    private String riskLevel;
    private Integer score;
    private ReviewReport report;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<EvidenceGroup> evidenceGroups = new ArrayList<>();
    private List<String> steps = new ArrayList<>();

    public static WorkflowResult of(SceneEnum scene, String answer) {
        WorkflowResult result = new WorkflowResult();
        result.setScene(scene);
        result.setAnswer(answer);
        return result;
    }

}
