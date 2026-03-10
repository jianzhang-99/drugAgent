package com.liang.drugagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Embedding 接口连通性测试
 */
@SpringBootTest
public class EmbeddingModelTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    public void testEmbedding() {
        String text = "中药饮片质量监管规范";
        System.out.println("===== 准备为文本生成向量: " + text + " =====");
        
        float[] vector = embeddingModel.embed(text);
        
        System.out.println("===== 生成成功! =====");
        System.out.println("向量维度: " + vector.length);
        if (vector.length > 0) {
            System.out.println("前 5 维数据: [" + vector[0] + ", " + vector[1] + ", " + vector[2] + ", " + vector[3] + ", " + vector[4] + "...]");
        }
        
        // text-embedding-v3 默认通常是 1024 维
        assertThat(vector).isNotEmpty();
        assertThat(vector.length).isGreaterThan(0);
    }

    @Test
    public void testBatchEmbedding() {
        List<String> texts = List.of("药品管理法", "疫苗管理法");
        System.out.println("===== 准备批量生成向量 =====");
        
        EmbeddingResponse response = embeddingModel.call(new org.springframework.ai.embedding.EmbeddingRequest(texts, null));
        
        System.out.println("===== 批量生成成功! =====");
        System.out.println("生成的向量个数: " + response.getResults().size());
        
        assertThat(response.getResults()).hasSize(2);
    }
}
