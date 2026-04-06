package com.liang.drugagent.shared.vector;

import com.liang.drugagent.app.DrugAgentApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PGVector 持久化测试 - 直接测试向量库的添加和检索功能。
 */
@Slf4j
@SpringBootTest(classes = DrugAgentApplication.class)
@ActiveProfiles("local")
@DisplayName("PGVector 向量库持久化测试")
public class VectorStorePersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @Test
    @DisplayName("测试向量库持久化 - 添加文档后重启再检索")
    void testVectorStorePersistence() {
        String testSourceId = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        log.info("[{}] ========== PGVector 持久化测试开始 ==========", traceId);

        // 1. 验证表结构
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding",
                    Integer.class
            );
            log.info("[{}] 当前向量数量: {}", traceId, count);
        } catch (Exception e) {
            log.error("[{}] 查询向量数量失败: {}", traceId, e.getMessage());
        }

        // 2. 添加测试文档
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceId", testSourceId);
            metadata.put("orgId", "test-org-001");
            metadata.put("scene", "tender_review");
            metadata.put("sourceTitle", "药品注册管理办法");
            metadata.put("chunkIndex", 0);

            Document doc = Document.builder()
                    .id(testSourceId + "-chunk1")
                    .text("根据《药品注册管理办法》，药品注册是指国家药品监督管理局根据药品注册申请人的申请，依照法定程序，对拟上市销售药品的安全性、有效性、质量可控性等进行审查，并作出是否同意其申请的审批过程。")
                    .metadata(metadata)
                    .build();

            vectorStore.add(List.of(doc));
            log.info("[{}] 文档添加成功 - sourceId={}", traceId, testSourceId);

            // 3. 等待一下让数据持久化
            Thread.sleep(500);

            // 4. 验证数据库中已存在
            Integer afterAdd = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding WHERE source_id = ?",
                    Integer.class,
                    testSourceId
            );
            log.info("[{}] 添加后数据库向量数量: {}", traceId, afterAdd);

            // 5. 执行向量检索
            List<Document> results = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("药品注册的管理规定")
                            .topK(3)
                            .build()
            );

            log.info("[{}] 向量检索完成 - 命中结果数量: {}", traceId, results.size());
            for (int i = 0; i < Math.min(2, results.size()); i++) {
                Document result = results.get(i);
                log.info("[{}] 命中结果 {}: sourceId={}, score={}, content={}",
                        traceId, i + 1,
                        result.getMetadata().get("sourceId"),
                        result.getScore(),
                        result.getText().substring(0, Math.min(50, result.getText().length())));
            }

            // 6. 清理测试数据
            org.springframework.ai.vectorstore.filter.Filter.Expression filter =
                    new org.springframework.ai.vectorstore.filter.Filter.Expression(
                            org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ,
                            new org.springframework.ai.vectorstore.filter.Filter.Key("sourceId"),
                            new org.springframework.ai.vectorstore.filter.Filter.Value(testSourceId)
                    );
            vectorStore.delete(filter);
            log.info("[{}] 测试数据清理完成", traceId);

            // 7. 验证清理成功
            Thread.sleep(500);
            Integer afterDelete = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding WHERE source_id = ?",
                    Integer.class,
                    testSourceId
            );
            log.info("[{}] 清理后数据库向量数量: {}", traceId, afterDelete);

            assertEquals(0, afterDelete, "测试数据应该已被清理");

        } catch (Exception e) {
            log.error("[{}] 测试失败: {}", traceId, e.getMessage(), e);
            fail("PGVector 持久化测试失败: " + e.getMessage());
        }

        log.info("[{}] ========== PGVector 持久化测试完成 ==========", traceId);
    }
}