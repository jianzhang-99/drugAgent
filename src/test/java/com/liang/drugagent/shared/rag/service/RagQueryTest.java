package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.app.DrugAgentApplication;
import com.liang.drugagent.shared.rag.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 知识问答检索链路测试（阶段三）。
 *
 * <p>验证要点：
 * 1. 向量检索是否命中已入库文档
 * 2. 证据上下文是否正确拼装
 * 3. LLM回答是否基于检索证据
 * 4. 过滤条件（orgId、scene）是否生效</p>
 */
@Slf4j
@SpringBootTest(classes = DrugAgentApplication.class)
@ActiveProfiles("local")
@DisplayName("RAG 知识问答检索链路测试")
public class RagQueryTest {

    @Autowired
    private RagService ragService;

    /**
     * 测试用例1：仅检索模式（不生成LLM回答）
     */
    @Test
    @DisplayName("测试1：仅检索模式 - 验证向量检索命中")
    void testRetrievalOnly() {
        String testOrgId = "test-org-001";
        String testQuestion = "什么是药品注册";

        log.info("========== 测试1：仅检索模式 ==========");
        log.info("[查询参数] orgId={}, question={}", testOrgId, testQuestion);

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId(testOrgId)
                .question(testQuestion)
                .topK(5)
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[检索结果] decision={}, reason={}, chunks={}",
                response.getDecision(), response.getReason(),
                response.getEvidenceChunks() != null ? response.getEvidenceChunks().size() : 0);

        // 验证决策结果
        assertNotNull(response.getDecision(), "决策结果不应为空");

        if (response.getDecision() == RagDecision.ANSWERED) {
            // 验证citations
            assertNotNull(response.getCitations(), "citations列表不应为空");
            log.info("[Citations] 共{}条", response.getCitations().size());

            // 打印前3条citation详情
            response.getCitations().stream().limit(3).forEach(citation -> {
                log.info("[Citation] sourceTitle={}, chunkId={}, snippet={}",
                        citation.getSourceTitle(),
                        citation.getChunkId(),
                        citation.getSnippet() != null ?
                                citation.getSnippet().substring(0, Math.min(100, citation.getSnippet().length())) : "null");
            });

            // 验证evidenceChunks
            assertNotNull(response.getEvidenceChunks(), "evidenceChunks不应为空");
            log.info("[EvidenceChunks] 共{}条", response.getEvidenceChunks().size());

            response.getEvidenceChunks().stream().limit(3).forEach(chunk -> {
                log.info("[Chunk] chunkId={}, content长度={}",
                        chunk.getChunkId(),
                        chunk.getContent() != null ? chunk.getContent().length() : 0);
            });
        } else if (response.getDecision() == RagDecision.NO_HIT) {
            log.warn("[检索结果] 未命中 - 可能原因：1）阶段二入库未执行 2）测试数据不存在 3）orgId不匹配");
        }

        log.info("========== 测试1完成 ==========");
    }

    /**
     * 测试用例2：完整问答模式（生成LLM回答）
     */
    @Test
    @DisplayName("测试2：完整问答模式 - 验证LLM回答生成")
    void testFullQueryWithAnswer() {
        String testOrgId = "test-org-001";
        String testQuestion = "什么是药品注册";

        log.info("========== 测试2：完整问答模式 ==========");
        log.info("[查询参数] orgId={}, question={}, needGenerateAnswer=true",
                testOrgId, testQuestion);

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId(testOrgId)
                .question(testQuestion)
                .topK(5)
                .needGenerateAnswer(true)
                .sessionId("test-session-" + System.currentTimeMillis())
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[响应结果] decision={}, reason={}, needHumanReview={}",
                response.getDecision(), response.getReason(), response.getNeedHumanReview());

        // 验证answer不为空
        assertNotNull(response.getAnswer(), "回答内容不应为空");
        log.info("[LLM回答] 长度={}", response.getAnswer().length());
        log.info("[LLM回答内容]\n{}", response.getAnswer());

        // 验证citations
        if (response.getCitations() != null && !response.getCitations().isEmpty()) {
            log.info("[Citations] 共{}条", response.getCitations().size());
            response.getCitations().stream().limit(3).forEach(citation -> {
                log.info("[Citation] sourceTitle={}, snippet={}",
                        citation.getSourceTitle(),
                        citation.getSnippet() != null ?
                                citation.getSnippet().substring(0, Math.min(80, citation.getSnippet().length())) : "null");
            });
        }

        // 评估回答质量
        if (response.getNeedHumanReview() != null && response.getNeedHumanReview()) {
            log.warn("[质量评估] 触发人工复核标记");
        } else {
            log.info("[质量评估] 回答质量正常");
        }

        log.info("========== 测试2完成 ==========");
    }

    /**
     * 测试用例3：过滤条件验证 - 错误orgId
     */
    @Test
    @DisplayName("测试3：过滤条件验证 - 错误orgId应返回NO_HIT")
    void testWrongOrgId() {
        String wrongOrgId = "non-existent-org-999";
        String testQuestion = "什么是药品注册";

        log.info("========== 测试3：错误orgId过滤 ==========");

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId(wrongOrgId)
                .question(testQuestion)
                .topK(5)
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[检索结果] decision={}, reason={}", response.getDecision(), response.getReason());

        // 错误orgId应该返回NO_HIT
        assertEquals(RagDecision.NO_HIT, response.getDecision(),
                "使用错误orgId查询应返回NO_HIT");

        log.info("========== 测试3完成 ==========");
    }

    /**
     * 测试用例4：过滤条件验证 - 正确orgId但不存在场景
     */
    @Test
    @DisplayName("测试4：过滤条件验证 - 正确orgId但不存在场景应返回NO_HIT")
    void testWrongScene() {
        String testOrgId = "test-org-001";
        String nonExistScene = "NON_EXISTENT_SCENE_999";

        log.info("========== 测试4：错误scene过滤 ==========");

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId(testOrgId)
                .scene(nonExistScene)
                .question("药品注册")
                .topK(5)
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[检索结果] decision={}, reason={}", response.getDecision(), response.getReason());

        // 正确orgId但错误scene应该返回NO_HIT
        assertEquals(RagDecision.NO_HIT, response.getDecision(),
                "使用错误scene查询应返回NO_HIT");

        log.info("========== 测试4完成 ==========");
    }

    /**
     * 测试用例5：参数校验 - 缺少orgId
     */
    @Test
    @DisplayName("测试5：参数校验 - 缺少orgId应返回错误")
    void testMissingOrgId() {
        log.info("========== 测试5：缺少orgId校验 ==========");

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId(null)
                .question("药品注册")
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[响应结果] decision={}, answer={}", response.getDecision(), response.getAnswer());

        assertEquals(RagDecision.NEED_HUMAN_REVIEW, response.getDecision(),
                "缺少orgId应返回NEED_HUMAN_REVIEW");
        assertTrue(response.getAnswer().contains("orgId"),
                "错误消息应提及orgId");

        log.info("========== 测试5完成 ==========");
    }

    /**
     * 测试用例6：参数校验 - question为空
     */
    @Test
    @DisplayName("测试6：参数校验 - question为空应返回NO_HIT")
    void testEmptyQuestion() {
        log.info("========== 测试6：空question校验 ==========");

        RagQueryRequest request = RagQueryRequest.builder()
                .orgId("test-org-001")
                .question("")
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse response = ragService.query(request);

        log.info("[响应结果] decision={}, reason={}", response.getDecision(), response.getReason());

        assertEquals(RagDecision.NO_HIT, response.getDecision(),
                "空question应返回NO_HIT");

        log.info("========== 测试6完成 ==========");
    }
}
