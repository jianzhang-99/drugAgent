package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbstractTenderExecutor 工具方法单元测试。
 *
 * <p>验证：
 * <ol>
 *   <li>编辑距离算法正确性</li>
 *   <li>电话号码标准化功能</li>
 *   <li>RuleHit 基础数据构建</li>
 *   <li>Field 转 RuleEvidence 转换</li>
 * </ol>
 */
class AbstractTenderExecutorTest {

    /**
     * 测试编辑距离算法 - 相同字符串距离为0
     */
    @Test
    void shouldEditDistance返回0当字符串相同时() {
        AbstractTenderExecutor executor = new TestableExecutor();
        assertEquals(0, executor.editDistance("蓝天博科", "蓝天博科"));
        assertEquals(0, executor.editDistance("", ""));
    }

    /**
     * 测试编辑距离算法 - 空字符串与非空字符串
     */
    @Test
    void shouldEditDistance返回正确值当字符串不同时() {
        AbstractTenderExecutor executor = new TestableExecutor();
        // 一个字符差异
        assertEquals(1, executor.editDistance("蓝天博科", "蓝天博"));
        // 完全不同
        assertEquals(4, executor.editDistance("abcd", "efgh"));
    }

    /**
     * 测试电话号码标准化 - 去除非数字字符
     */
    @Test
    void shouldNormalizePhone去除非数字字符() {
        AbstractTenderExecutor executor = new TestableExecutor();
        assertEquals("13812345678", executor.normalizePhone("138-1234-5678"));
        assertEquals("13812345678", executor.normalizePhone("138 1234 5678"));
        assertEquals("8613912345678", executor.normalizePhone("+86 139 1234 5678"));
        assertEquals("13912345678", executor.normalizePhone("(139) 1234-5678"));
    }

    /**
     * 测试电话号码标准化 - null和空字符串
     */
    @Test
    void shouldNormalizePhone处理Null和空字符串() {
        AbstractTenderExecutor executor = new TestableExecutor();
        assertNull(executor.normalizePhone(null));
        assertEquals("", executor.normalizePhone("   "));
    }

    /**
     * 测试创建基础RuleHit
     */
    @Test
    void shouldCreateBaseHit正确设置所有字段() {
        AbstractTenderExecutor executor = new TestableExecutor();

        RuleHit hit = executor.createBaseHit(
                "W-M2",
                "联系方式近邻",
                "scope-1",
                "collusion",
                "VERY_HIGH",
                "v1"
        );

        assertNotNull(hit.getHitId());
        assertEquals("W-M2", hit.getRuleCode());
        assertEquals("联系方式近邻", hit.getRuleName());
        assertEquals("scope-1", hit.getScopeId());
        assertEquals("collusion", hit.getRiskType());
        assertEquals("VERY_HIGH", hit.getPriority());
        assertEquals("v1", hit.getVersion());
    }

    /**
     * 测试Field转RuleEvidence转换
     */
    @Test
    void shouldToEvidence正确转换Field所有字段() {
        AbstractTenderExecutor executor = new TestableExecutor();

        Field field = Field.builder()
                .fieldId("field-1")
                .documentId("doc-1")
                .blockId("block-1")
                .normalizedValue("13812345678")
                .chapterPath("3.2 联系方式")
                .anchorParagraphIndex(5)
                .anchorTableIndex(-1)
                .anchorPageNo(3)
                .anchorSectionNo("3.2")
                .anchorParagraphNo(5)
                .anchorTableNo(-1)
                .build();

        RuleEvidence evidence = executor.toEvidence(field);

        assertEquals("doc-1", evidence.getDocumentId());
        assertEquals("field-1", evidence.getFieldId());
        assertEquals("block-1", evidence.getBlockId());
        assertEquals("13812345678", evidence.getMatchedValue());
        assertEquals("3.2 联系方式", evidence.getChapterPath());
        assertEquals(5, evidence.getAnchorParagraphIndex());
        assertEquals(-1, evidence.getAnchorTableIndex());
        assertEquals(3, evidence.getAnchorPageNo());
        assertEquals("3.2", evidence.getAnchorSectionNo());
        assertEquals(5, evidence.getAnchorParagraphNo());
        assertEquals(-1, evidence.getAnchorTableNo());
    }

    /**
     * 测试Field为null时toEvidence返回null
     */
    @Test
    void shouldToEvidence返回Null当Field为Null时() {
        AbstractTenderExecutor executor = new TestableExecutor();
        assertNull(executor.toEvidence(null));
    }

    /**
     * 用于测试的工具子类
     */
    private static class TestableExecutor extends AbstractTenderExecutor {
        @Override
        public java.util.List<com.liang.drugagent.scene.tender_review.model.RuleHit> execute(
                com.liang.drugagent.scene.tender_review.model.TenderReviewData data) {
            return java.util.List.of();
        }
    }
}
