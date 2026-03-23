package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.*;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.ReviewReport;

import java.util.List;

/**
 * 报告生成步骤。
 *
 * @author liangjiajian
 */
public interface ReportGenerationStep {

    /**
     * 生成审查报告。
     */
    ReviewReport generate(TenderReviewData tenderReviewData,
                          List<RuleHit> allHits,
                          List<RuleHit> effectiveHits,
                          List<ExemptionHit> exemptionHits,
                          RiskFusionResult fusionResult,
                          EvidenceAssemblyResult evidenceAssemblyResult);

    /**
     * 构建回答文本。
     */
    String buildAnswer(ReviewReport report);
}
