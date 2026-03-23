package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.*;
import com.liang.drugagent.core.domain.model.EvidenceAssemblyResult;
import com.liang.drugagent.core.domain.model.ReviewReport;

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
