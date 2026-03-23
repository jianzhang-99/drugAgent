package com.liang.drugagent.interfaces.http.response.agent;

import com.liang.drugagent.core.domain.model.EvidenceItem;
import com.liang.drugagent.core.domain.model.EvidenceGroup;
import com.liang.drugagent.core.domain.model.ReviewReport;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drug Agent 响应对象。
 *
 * @author liangjiajian
 */
@Setter
@Getter
public class DrugAgentResp {

    private String sessionId;
    private String traceId;
    private String scene;
    private String routeReason;
    private String routeSource;
    private Double confidence;
    private String summary;
    private String answer;
    private String riskLevel;
    private int score;
    private int docCount;
    private String managementSummary;
    private List<String> suggestedActions = new ArrayList<>();
    private String caseId;
    private List<String> documentIds = new ArrayList<>();
    private ReviewReport report;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<EvidenceGroup> evidenceGroups = new ArrayList<>();
    private List<String> steps = new ArrayList<>();
    private Map<String, Object> structuredData;
    private boolean requiresClarification;
    private String clarificationQuestion;

}
