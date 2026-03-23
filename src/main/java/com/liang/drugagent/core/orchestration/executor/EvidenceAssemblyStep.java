package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RiskFusionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.core.domain.model.EvidenceAssemblyResult;

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
