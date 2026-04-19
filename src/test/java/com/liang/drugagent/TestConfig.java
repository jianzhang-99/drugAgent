package com.liang.drugagent;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 测试配置类。
 *
 * <p>用于 @WebMvcTest 和 @SpringBootTest 切片测试时提供必要的 Bean。</p>
 */
@SpringBootConfiguration
public class TestConfig {

    @Bean
    @Primary
    public VectorStore testVectorStore() {
        // 返回一个 mock VectorStore，实际测试中会使用 @MockBean 覆盖
        throw new UnsupportedOperationException("请使用 @MockBean 注入 mock VectorStore");
    }
}
