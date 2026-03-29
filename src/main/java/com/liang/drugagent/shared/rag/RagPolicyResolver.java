package com.liang.drugagent.shared.rag;

import com.liang.drugagent.scene.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按场景解析 RAG 策略。
 */
@Component
public class RagPolicyResolver {

    public RagPolicy resolve(SceneEnum scene) {
        return resolve(scene, null, null, null);
    }

    public RagPolicy resolve(SceneEnum scene,
                             String strategyCode,
                             String reviewFocus,
                             List<String> tags) {
        RagPolicy base = buildBasePolicy(scene);
        if (strategyCode == null || strategyCode.isBlank()) {
            return base;
        }

        RagPolicy.RagPolicyBuilder builder = base.toBuilder();
        switch (strategyCode) {
            case "PLAGIARISM" -> builder.keywordWeight(0.35)
                    .vectorWeight(0.65)
                    .minScore(base.getMinScore() - 0.02)
                    .minEvidence(Math.max(2, base.getMinEvidence() - 1));
            case "TEMPLATE_HOMOLOGY" -> builder.keywordWeight(0.55)
                    .vectorWeight(0.45)
                    .minScore(base.getMinScore())
                    .minEvidence(base.getMinEvidence());
            default -> {
            }
        }

        if (reviewFocus != null && reviewFocus.contains("严格")) {
            builder.minScore(base.getMinScore() + 0.02);
        }
        if (tags != null && tags.contains("high-risk")) {
            builder.minEvidence(base.getMinEvidence() + 1);
        }
        return builder.build();
    }

    private RagPolicy buildBasePolicy(SceneEnum scene) {
        if (scene == SceneEnum.CONTRACT_PRECHECK) {
            return RagPolicy.builder()
                    .topK(6)
                    .keywordTopN(8)
                    .vectorTopN(8)
                    .keywordWeight(0.6)
                    .vectorWeight(0.4)
                    .useRrf(true)
                    .rrfK(60)
                    .minScore(0.62)
                    .minEvidence(2)
                    .riskLevel("MEDIUM")
                    .build();
        }
        if (scene == SceneEnum.RISK_ALERT) {
            return RagPolicy.builder()
                    .topK(6)
                    .keywordTopN(8)
                    .vectorTopN(8)
                    .keywordWeight(0.5)
                    .vectorWeight(0.5)
                    .useRrf(true)
                    .rrfK(60)
                    .minScore(0.65)
                    .minEvidence(2)
                    .riskLevel("PENDING")
                    .build();
        }
        if (scene == SceneEnum.TENDER_REVIEW) {
            return RagPolicy.builder()
                    .topK(8)
                    .keywordTopN(10)
                    .vectorTopN(10)
                    .keywordWeight(0.4)
                    .vectorWeight(0.6)
                    .useRrf(true)
                    .rrfK(60)
                    .minScore(0.66)
                    .minEvidence(3)
                    .riskLevel("NONE")
                    .build();
        }
        return RagPolicy.builder()
                .topK(5)
                .keywordTopN(6)
                .vectorTopN(6)
                .keywordWeight(0.5)
                .vectorWeight(0.5)
                .useRrf(true)
                .rrfK(60)
                .minScore(0.68)
                .minEvidence(2)
                .riskLevel("UNKNOWN")
                .build();
    }
}
