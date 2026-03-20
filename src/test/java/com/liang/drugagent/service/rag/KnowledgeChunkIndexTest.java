package com.liang.drugagent.service.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class KnowledgeChunkIndexTest {

    @Test
    void bm25_shouldPreferMoreRelevantChunk() {
        KnowledgeChunkIndex index = new KnowledgeChunkIndex();

        KnowledgeChunkIndex.Entry e1 = new KnowledgeChunkIndex.Entry();
        e1.setChunkId("c1");
        e1.setContent("高值耗材 异常 增长 预警 规则");

        KnowledgeChunkIndex.Entry e2 = new KnowledgeChunkIndex.Entry();
        e2.setChunkId("c2");
        e2.setContent("合同 条款 审核 要点");

        index.addAll(List.of(e1, e2));

        Map<String, Double> scores = index.bm25Search(index.all(), List.of("耗材", "预警"), 5);

        Assertions.assertTrue(scores.containsKey("c1"));
        Assertions.assertFalse(scores.containsKey("c2"));
    }
}
