package com.liang.drugagent.scene.tender_review.model;

import com.liang.drugagent.shared.model.EvidenceGroup;
import com.liang.drugagent.shared.model.EvidenceItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书审查 RAG 法规证据结果。
 *
 * <p>RAG 在标书审查中只作为法规、标准、案例的证据增强层，
 * 不直接改变 Workflow 的风险等级和评分。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderReviewRagEvidence {

    /**
     * SUPPORTED / NO_HIT / SKIPPED / DEGRADED
     */
    private String status;

    private String reason;

    @Builder.Default
    private int hitCount = 0;

    @Builder.Default
    private List<EvidenceItem> items = new ArrayList<>();

    private EvidenceGroup group;

    public static TenderReviewRagEvidence skipped(String reason) {
        return TenderReviewRagEvidence.builder()
                .status("SKIPPED")
                .reason(reason)
                .build();
    }

    public static TenderReviewRagEvidence noHit(String reason) {
        return TenderReviewRagEvidence.builder()
                .status("NO_HIT")
                .reason(reason)
                .build();
    }

    public static TenderReviewRagEvidence degraded(String reason) {
        return TenderReviewRagEvidence.builder()
                .status("DEGRADED")
                .reason(reason)
                .build();
    }

    public boolean hasEvidence() {
        return group != null && group.getItems() != null && !group.getItems().isEmpty();
    }
}
