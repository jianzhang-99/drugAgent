package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;

import java.util.List;

/**
 * 证据组装步骤。
 *
 * @author liangjiajian
 */
public interface EvidenceAssemblyStep {

    /**
     * 组装证据。
     */
    EvidenceAssemblyResult assemble(List<RuleHit> effectiveHits,
                                   List<ExemptionHit> exemptionHits,
                                   RiskFusionResult fusionResult);
}
