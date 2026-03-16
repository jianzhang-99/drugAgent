package com.liang.drugagent.domain.resp;

import com.liang.drugagent.workflow.SceneType;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流执行结果
 */
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

    public SceneType getScene() {
        return scene;
    }

    public void setScene(SceneType scene) {
        this.scene = scene;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<EvidenceItem> getEvidenceList() {
        return evidenceList;
    }

    public void setEvidenceList(List<EvidenceItem> evidenceList) {
        this.evidenceList = evidenceList;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }
}
