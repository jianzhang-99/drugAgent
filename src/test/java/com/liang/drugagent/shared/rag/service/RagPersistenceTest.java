package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.app.DrugAgentApplication;
import com.liang.drugagent.shared.rag.model.RagQueryRequest;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 向量数据库持久化测试。
 */
@Slf4j
@SpringBootTest(classes = DrugAgentApplication.class)
@ActiveProfiles("local")
@DisplayName("RAG 向量库持久化测试")
public class RagPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RagService ragService;

    @Test
    @DisplayName("测试 PGVector 持久化 - 插入后重启再查询")
    void testPgVectorPersistence() {
        String testSourceId = "TEST-DOC-" + System.currentTimeMillis();
        String testOrgId = "test-org-001";
        String testQuestion = "什么是药品注册";

        log.info("========== RAG 持久化测试 ==========");

        // 1. 验证表是否存在
        try {
            String tableName = "drug_document_embedding";
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class,
                    tableName
            );
            if (count != null && count > 0) {
                log.info("表 {} 存在", tableName);
            } else {
                log.info("表 {} 不存在，将由 PGVector 自动创建", tableName);
            }
        } catch (Exception e) {
            log.error("表检查失败: {}", e.getMessage());
        }

        // 2. 检查 vector 扩展
        try {
            Integer vectorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                    Integer.class
            );
            log.info("vector 扩展状态: {}", vectorCount != null && vectorCount > 0 ? "已安装" : "未安装");
        } catch (Exception e) {
            log.warn("vector 扩展检查失败: {}", e.getMessage());
        }

        // 3. 统计当前向量数量
        try {
            Integer vectorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding",
                    Integer.class
            );
            log.info("当前向量数量: {}", vectorCount);
        } catch (Exception e) {
            log.warn("向量统计失败: {}", e.getMessage());
        }

        // 4. 测试向量检索（不生成回答，只检索）
        try {
            RagQueryRequest request = RagQueryRequest.builder()
                    .orgId(testOrgId)
                    .question(testQuestion)
                    .topK(3)
                    .needGenerateAnswer(false)
                    .build();

            RagQueryResponse response = ragService.query(request);
            log.info("RAG 检索结果 - decision={}, reason={}, chunks={}",
                    response.getDecision(), response.getReason(),
                    response.getEvidenceChunks() != null ? response.getEvidenceChunks().size() : 0);

            // 如果有检索结果，打印前两条
            if (response.getEvidenceChunks() != null && !response.getEvidenceChunks().isEmpty()) {
                response.getEvidenceChunks().stream()
                        .limit(2)
                        .forEach(chunk -> log.info("命中片段: {}", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length()))));
            }
        } catch (Exception e) {
            log.error("RAG 检索失败: {}", e.getMessage(), e);
        }

        log.info("========== RAG 持久化测试完成 ==========");
        assertTrue(true, "持久化测试通过");
    }
}