package com.liang.drugagent.shared.vector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 向量数据库配置。
 *
 * <p>使用 Spring AI 原生 PgVectorStore，配置从 application-local.yml 读取。
 * 由于项目同时使用 MySQL（业务数据）和 PostgreSQL（向量库），
 * 这里单独创建 PostgreSQL 数据源供 PGVector 使用。</p>
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * PGVector 配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "pgvector")
    public PgVectorProperties pgVectorProperties() {
        return new PgVectorProperties();
    }

    /**
     * PostgreSQL 数据源（专供 PGVector 使用）
     */
    @Bean
    public DataSource pgVectorDataSource(PgVectorProperties properties) {
        HikariConfig config = new HikariConfig();
        // 确保 URL 包含 sslmode=disable
        String url = properties.getUrl();
        if (!url.contains("sslmode")) {
            url = url + (url.contains("?") ? "&" : "?") + "sslmode=disable";
        }
        config.setJdbcUrl(url);
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        // 连接属性
        config.addDataSourceProperty("connectTimeout", "10");
        config.addDataSourceProperty("loginTimeout", "10");
        config.addDataSourceProperty("tcpKeepAlive", "true");

        log.info("PGVector 数据源创建 - url={}", config.getJdbcUrl());
        return new HikariDataSource(config);
    }

    /**
     * PostgreSQL JdbcTemplate（专供 PGVector 使用）。
     * 标记 @Primary 确保 PgVectorStore 默认使用此 JdbcTemplate。
     * 添加 @Qualifier("pgVectorJdbcTemplate") 确保可被精确注入。
     */
    @Bean("pgVectorJdbcTemplate")
    @Primary
    public JdbcTemplate pgVectorJdbcTemplate(DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }

    /**
     * 配置 PGVector 向量数据库。
     *
     * <p>使用 Spring AI 1.1.4 原生 PgVectorStore，支持：
     * - 持久化存储到 PostgreSQL
     * - 按 sourceId 删除（原生支持 Filter API）
     * - 向量相似度检索
     * - metadata 过滤</p>
     *
     * <p>注意：直接在方法内部使用 pgVectorDataSource 创建 JdbcTemplate，
     * 避免因 Spring Boot 多 DataSource 配置导致的注入混乱。</p>
     *
     * @param embeddingModel Spring AI 自动注入的 Embedding 模型
     * @param properties PGVector 配置属性
     * @return PGVector 向量数据库实例
     */
    @Bean
    public PgVectorStore pgVectorStore(EmbeddingModel embeddingModel,
                                        PgVectorProperties properties) {
        // 直接使用 pgVectorDataSource 创建 JdbcTemplate，避免 Spring 注入混乱
        JdbcTemplate pgVectorJdbcTemplate = new JdbcTemplate(pgVectorDataSource(properties));

        PgVectorStore pgVectorStore = PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .vectorTableName(properties.getTableName())
                .dimensions(properties.getDimension())
                .initializeSchema(true)
                .build();

        log.info("PGVector 向量库初始化完成 - table={}, dimension={}",
                properties.getTableName(), properties.getDimension());
        return pgVectorStore;
    }

    /**
     * PGVector 配置属性
     */
    public static class PgVectorProperties {
        private String url;
        private String username;
        private String password;
        private Integer dimension = 1536;
        private String tableName = "vector_store";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }
}
