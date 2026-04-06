package com.liang.drugagent.shared.tool;

import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.rag.adapter.RagOutcomeAdapter;
import com.liang.drugagent.shared.rag.model.RagQueryRequest;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import com.liang.drugagent.shared.rag.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识检索 Tool。
 *
 * <p>供场景 Orchestrator 调用，作为 RAG 模块接入 Agent 主链路的统一入口。
 * 支持按业务场景、文档类型、topicTags 等条件检索知识库。</p>
 */
@Slf4j
@Component
public class KnowledgeRetrievalTool {

    private final RagService ragService;
    private final RagOutcomeAdapter ragOutcomeAdapter;

    public KnowledgeRetrievalTool(RagService ragService, RagOutcomeAdapter ragOutcomeAdapter) {
        this.ragService = ragService;
        this.ragOutcomeAdapter = ragOutcomeAdapter;
    }

    /**
     * 执行知识检索。
     *
     * @param question 用户问题
     * @param orgId 组织ID（必填）
     * @param scene 业务场景（可选）
     * @param subScene 子场景（可选）
     * @param docType 文档类型（可选）
     * @param topicTags 主题标签列表（可选）
     * @param topK 返回数量，默认5
     * @param needGenerateAnswer 是否需要LLM生成回答，默认true
     * @return 检索结果
     */
    public RagOutcome retrieve(String question, String orgId, String scene, String subScene,
                              String docType, List<String> topicTags, Integer topK,
                              Boolean needGenerateAnswer) {
        if (orgId == null || orgId.isBlank()) {
            log.warn("[KnowledgeRetrievalTool] orgId 为空，跳过检索");
            return RagOutcome.builder()
                    .decision("NO_HIT")
                    .reason("CONTEXT_MISSING")
                    .answer("检索失败：缺少组织标识")
                    .build();
        }

        if (question == null || question.isBlank()) {
            log.warn("[KnowledgeRetrievalTool] question 为空，跳过检索");
            return RagOutcome.builder()
                    .decision("NO_HIT")
                    .reason("NO_CANDIDATE")
                    .answer("检索失败：问题内容为空")
                    .build();
        }

        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] 知识检索开始 - orgId={}, scene={}, question={}", traceId, orgId, scene, question);

        try {
            RagQueryRequest request = RagQueryRequest.builder()
                    .question(question)
                    .orgId(orgId)
                    .scene(scene)
                    .subScene(subScene)
                    .docType(docType)
                    .topicTags(topicTags)
                    .topK(topK != null ? topK : 5)
                    .needGenerateAnswer(needGenerateAnswer != null ? needGenerateAnswer : true)
                    .build();

            RagQueryResponse response = ragService.query(request);
            RagOutcome outcome = ragOutcomeAdapter.toRagOutcome(response);

            log.info("[{}] 知识检索完成 - decision={}, needHumanReview={}",
                    traceId, outcome.getDecision(), response.getNeedHumanReview());
            return outcome;

        } catch (Exception e) {
            log.error("[{}] 知识检索异常", traceId, e);
            return RagOutcome.builder()
                    .decision("NEED_HUMAN_REVIEW")
                    .reason("LOW_CONFIDENCE")
                    .answer("检索过程中发生异常，请人工确认。错误信息：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 执行知识检索（简化版）。
     *
     * @param question 用户问题
     * @param orgId 组织ID（必填）
     * @param scene 业务场景
     * @return 检索结果
     */
    public RagOutcome retrieve(String question, String orgId, String scene) {
        return retrieve(question, orgId, scene, null, null, null, null, true);
    }

    /**
     * 执行知识检索（仅检索，不生成回答）。
     *
     * @param question 用户问题
     * @param orgId 组织ID（必填）
     * @param scene 业务场景
     * @param docType 文档类型
     * @param topK 返回数量
     * @return 仅包含检索片段的结果
     */
    public RagOutcome search(String question, String orgId, String scene, String docType, Integer topK) {
        return retrieve(question, orgId, scene, null, docType, null, topK, false);
    }
}
