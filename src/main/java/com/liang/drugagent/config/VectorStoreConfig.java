package com.liang.drugagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class VectorStoreConfig {

    /**
     * 配置基于内存的向量数据库 (SimpleVectorStore)
     * 用于 Demo 阶段快速验证，支持持久化到本地文件
     *
     * @param embeddingModel Spring AI 自动注入的 Embedding 模型 (即 text-embedding-v3)
     * @return 向量数据库实例
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        
        // 尝试从本地加载已有的向量数据，实现重启不丢失
        File vectorStoreFile = new File("vector_store.json");
        if (vectorStoreFile.exists()) {
            simpleVectorStore.load(vectorStoreFile);
            System.out.println("✅ 已从本地缓存加载向量数据: vector_store.json");
        }
        
        return simpleVectorStore;
    }
}
