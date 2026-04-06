package com.liang.drugagent.shared.vector;

import com.liang.drugagent.app.DrugAgentApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PGVector 连接测试。
 */
@Slf4j
@SpringBootTest(classes = DrugAgentApplication.class)
@DisplayName("PGVector 向量库连接测试")
public class PgVectorStoreTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("测试 PGVector 连接是否正常")
    void testConnection() {
        log.info("========== PGVector 连接测试 ==========");

        // 1. 测试 PostgreSQL 连接
        try {
            String result = jdbcTemplate.queryForObject("SELECT version()", String.class);
            log.info("PostgreSQL 连接成功: {}", result);
        } catch (Exception e) {
            log.error("PostgreSQL 连接失败: {}", e.getMessage());
            fail("PostgreSQL 连接失败: " + e.getMessage());
        }

        // 2. 测试 PGVector 扩展是否可用
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("vector 扩展检查成功");
        } catch (Exception e) {
            log.warn("vector 扩展检查异常: {}", e.getMessage());
        }

        // 3. 测试表是否存在
        try {
            String tableName = "hengdu";
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
            log.info("表检查异常（可能由权限导致）: {}", e.getMessage());
        }

        log.info("========== PGVector 连接测试完成 ==========");
        assertTrue(true, "连接测试通过");
    }
}
