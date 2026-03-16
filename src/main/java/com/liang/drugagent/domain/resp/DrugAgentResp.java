package com.liang.drugagent.domain.resp;

import com.liang.drugagent.domain.EvidenceItem;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Drug Agent 响应对象。
 *
 * @author liangjiajian
 */
@Setter
@Getter
public class DrugAgentResp {

    private String traceId;
    private String scene;
    private String routeReason;
    private String answer;
    private String riskLevel;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
    private List<String> steps = new ArrayList<>();

}
