package com.liang.drugagent.shared.vector;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PGVector 持久化测试 - 直接通过 JDBC 插入和检索向量数据。
 */
@Slf4j
@SpringBootTest(classes = com.liang.drugagent.app.DrugAgentApplication.class)
@ActiveProfiles("local")
@DisplayName("PGVector 直接持久化测试")
public class PgVectorDirectPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int VECTOR_DIMENSION = 1536;

    @Test
    @DisplayName("测试 PGVector 直接 SQL 持久化")
    void testPgVectorDirectPersistence() {
        String testId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        log.info("[{}] ========== PGVector 直接持久化测试开始 ==========", traceId);

        try {
            // 1. 生成随机向量（用于测试）
            float[] vector = new float[VECTOR_DIMENSION];
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                vector[i] = (float) (Math.random() * 2 - 1); // 范围 [-1, 1]
            }

            // 2. 插入测试向量
            String content = "根据《药品注册管理办法》，药品注册是指国家药品监督管理局根据药品注册申请人的申请，依照法定程序，对拟上市销售药品的安全性、有效性、质量可控性等进行审查。";

            String metadataJson = String.format(
                    "{\"sourceId\":\"%s\",\"orgId\":\"test-org-001\",\"scene\":\"tender_review\",\"sourceTitle\":\"药品注册管理办法\",\"chunkIndex\":0}",
                    testId
            );

            // 使用 Spring AI 的 PGVector 格式直接插入
            // PGVector 表结构: id(uuid), content(text), metadata(json), embedding(vector)
            jdbcTemplate.update(
                    "INSERT INTO drug_document_embedding (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::json, ?)",
                    testId, content, metadataJson, vector
            );

            log.info("[{}] 向量插入成功 - id={}", traceId, testId);

            // 3. 验证插入成功
            Thread.sleep(200);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding WHERE id = ?::uuid",
                    Integer.class,
                    testId
            );
            log.info("[{}] 数据库验证 - 记录数量: {}", traceId, count);
            assertEquals(1, count, "向量应该已被插入");

            // 4. 执行向量检索 - 通过 metadata 的 jsonb 查询
            var results = jdbcTemplate.queryForList(
                    "SELECT id, content, metadata FROM drug_document_embedding WHERE id = ?::uuid LIMIT 3",
                    testId
            );
            log.info("[{}] 向量检索结果数量: {}", traceId, results.size());

            if (!results.isEmpty()) {
                var row = results.get(0);
                log.info("[{}] 检索命中 - id={}, content长度={}", traceId, row.get("id"), ((String)row.get("content")).length());
            }

            // 5. 清理测试数据
            jdbcTemplate.update("DELETE FROM drug_document_embedding WHERE id = ?::uuid", testId);
            log.info("[{}] 测试数据清理完成", traceId);

            Thread.sleep(200);

            Integer afterDelete = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding WHERE id = ?::uuid",
                    Integer.class,
                    testId
            );
            assertEquals(0, afterDelete, "测试数据应该已被清理");

            log.info("[{}] ========== PGVector 直接持久化测试成功 ==========", traceId);

        } catch (Exception e) {
            log.error("[{}] 测试失败: {}", traceId, e.getMessage(), e);
            fail("PGVector 持久化测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("验证 PGVector 表结构和扩展状态")
    void testPgVectorStructure() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] ========== PGVector 结构验证开始 ==========", traceId);

        try {
            // 1. 检查 vector 扩展
            Integer vectorExt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                    Integer.class
            );
            log.info("[{}] vector 扩展: {}", traceId, vectorExt != null && vectorExt > 0 ? "已安装" : "未安装");
            assertTrue(vectorExt != null && vectorExt > 0, "vector 扩展应该已安装");

            // 2. 检查表是否存在
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'drug_document_embedding'",
                    Integer.class
            );
            log.info("[{}] 表 drug_document_embedding: {}", traceId, tableExists != null && tableExists > 0 ? "存在" : "不存在");
            assertTrue(tableExists != null && tableExists > 0, "表应该存在");

            // 3. 检查表结构
            var columns = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'drug_document_embedding'"
            );
            log.info("[{}] 表结构列:", traceId);
            for (var col : columns) {
                log.info("[{}]   - {}: {}", traceId, col.get("column_name"), col.get("data_type"));
            }

            // 4. 检查向量数量
            Integer vectorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_document_embedding",
                    Integer.class
            );
            log.info("[{}] 当前向量数量: {}", traceId, vectorCount);

            // 5. 检查索引
            var indexes = jdbcTemplate.queryForList(
                    "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'drug_document_embedding'"
            );
            log.info("[{}] 表索引:", traceId);
            for (var idx : indexes) {
                log.info("[{}]   - {}", traceId, idx.get("indexname"));
            }

            log.info("[{}] ========== PGVector 结构验证成功 ==========", traceId);

        } catch (Exception e) {
            log.error("[{}] 验证失败: {}", traceId, e.getMessage(), e);
            fail("PGVector 结构验证失败: " + e.getMessage());
        }
    }
}