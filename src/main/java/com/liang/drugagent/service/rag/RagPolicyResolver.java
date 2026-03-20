package com.liang.drugagent.service.rag;

import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

/**
 * 按场景解析 RAG 策略。
 *
 * 设计原则：
 * - 合同场景偏关键词（条款号、术语）
 * - 风险预警场景关键词与语义均衡
 * - 标书场景偏语义（雷同/近义改写）
 */
@Component
public class RagPolicyResolver {

    public RagPolicy resolve(SceneEnum scene) {
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
