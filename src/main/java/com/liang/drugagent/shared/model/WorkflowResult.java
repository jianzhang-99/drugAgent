package com.liang.drugagent.shared.model;

import com.liang.drugagent.scene.SceneEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private String summary;
    private String answer;
    private String riskLevel;
    private Integer score;
    private ReviewReport report;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<EvidenceGroup> evidenceGroups = new ArrayList<>();
    private List<String> steps = new ArrayList<>();
    private List<ThinkingStep> thinkingSteps = new ArrayList<>();
    /**
     * 分析覆盖度信息，记录各 LLM 分析器的执行状态。
     * key: 分析器名称（如 W-P1、W-P2 等）
     * value: 分析器状态 SUCCESS / FAILED
     */
    private Map<String, String> analyzerStatus = new LinkedHashMap<>();

    public static WorkflowResult of(SceneEnum scene, String answer) {
        WorkflowResult result = new WorkflowResult();
        result.setScene(scene);
        result.setAnswer(answer);
        return result;
    }

}
