package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.service.tenderreview.EvidenceAssemblerService;
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
