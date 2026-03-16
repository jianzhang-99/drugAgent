package com.liang.drugagent.domain.resp;

import java.util.ArrayList;
import java.util.List;

/**
 * Drug Agent 响应对象
 */
public class DrugAgentResp {

    private String traceId;
    private String scene;
    private String routeReason;
    private String answer;
    private String riskLevel;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<String> steps = new ArrayList<>();

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getAnswer() {
        return answer;
    }

    public String getRouteReason() {
        return routeReason;
    }

    public void setRouteReason(String routeReason) {
        this.routeReason = routeReason;
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
