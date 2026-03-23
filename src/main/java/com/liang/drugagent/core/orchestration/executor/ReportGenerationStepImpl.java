package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.*;
import com.liang.drugagent.core.domain.model.EvidenceAssemblyResult;
import com.liang.drugagent.core.domain.model.ReviewReport;
import com.liang.drugagent.scenes.tender_review.application.services.ReportGenerationService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 报告生成步骤实现。
 *
 * @author liangjiajian
 */
@Component
public class ReportGenerationStepImpl implements ReportGenerationStep {

    private final ReportGenerationService reportGenerationService;

    public ReportGenerationStepImpl(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @Override
    public ReviewReport generate(TenderReviewData tenderReviewData,
                               List<RuleHit> allHits,
                               List<RuleHit> effectiveHits,
                               List<ExemptionHit> exemptionHits,
                               RiskFusionResult fusionResult,
                               EvidenceAssemblyResult evidenceAssemblyResult) {
        return reportGenerationService.generate(
                tenderReviewData,
                allHits,
                effectiveHits,
                exemptionHits,
                fusionResult,
                evidenceAssemblyResult
        );
    }

    @Override
    public String buildAnswer(ReviewReport report) {
        return reportGenerationService.buildAnswer(report);
    }
}
