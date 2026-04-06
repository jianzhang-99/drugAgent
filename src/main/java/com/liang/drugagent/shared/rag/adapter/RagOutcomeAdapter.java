package com.liang.drugagent.shared.rag.adapter;

import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.rag.model.RagCitation;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 适配器。
 *
 * <p>负责将 RAG 查询结果转换为 Workflow 可消费的 RagOutcome。</p>
 */
@Component
public class RagOutcomeAdapter {

    /**
     * 将 RagQueryResponse 转换为 RagOutcome
     */
    public RagOutcome toRagOutcome(RagQueryResponse response) {
        if (response == null) {
            return null;
        }

        List<EvidenceItem> evidenceItems = new ArrayList<>();
        if (response.getCitations() != null) {
            for (RagCitation citation : response.getCitations()) {
                EvidenceItem item = EvidenceItem.builder()
                        .title(citation.getSourceTitle())
                        .content(citation.getSnippet())
                        .source("[" + citation.getChunkId() + "] " + citation.getSourceId())
                        .build();
                evidenceItems.add(item);
            }
        }

        return RagOutcome.builder()
                .answer(response.getAnswer())
                .decision(response.getDecision() != null ? response.getDecision().name() : null)
                .reason(response.getReason() != null ? response.getReason().name() : null)
                .riskLevel(response.getRiskLevel())
                .evidenceList(evidenceItems)
                .needHumanReview(Boolean.TRUE.equals(response.getNeedHumanReview()))
                .build();
    }
}
