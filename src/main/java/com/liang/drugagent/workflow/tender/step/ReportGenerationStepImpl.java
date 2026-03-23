package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.*;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.ReviewReport;
import com.liang.drugagent.service.tenderreview.ReportGenerationService;
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
