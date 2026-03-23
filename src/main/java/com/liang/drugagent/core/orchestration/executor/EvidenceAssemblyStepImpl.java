package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RiskFusionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.core.domain.model.EvidenceAssemblyResult;
import com.liang.drugagent.scenes.tender_review.application.services.EvidenceAssemblerService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 证据组装步骤实现。
 *
 * @author liangjiajian
 */
@Component
public class EvidenceAssemblyStepImpl implements EvidenceAssemblyStep {

    private final EvidenceAssemblerService evidenceAssemblerService;

    public EvidenceAssemblyStepImpl(EvidenceAssemblerService evidenceAssemblerService) {
        this.evidenceAssemblerService = evidenceAssemblerService;
    }

    @Override
    public EvidenceAssemblyResult assemble(List<RuleHit> effectiveHits,
                                          List<ExemptionHit> exemptionHits,
                                          RiskFusionResult fusionResult) {
        return evidenceAssemblerService.assemble(effectiveHits, exemptionHits, fusionResult);
    }
}
