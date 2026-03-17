package com.liang.drugagent.executor;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.TemplateHomologyExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版式模板同源规则执行器单元测试。
 *
 * @author liangjiajian
 */
class TemplateHomologyExecutorTest {

    private final TemplateHomologyExecutor executor = new TemplateHomologyExecutor();

    /**
     * 验证当章节标题高度一致时命中 W-M4。
     */
    @Test
    void shouldHitWhenHeadingsAreHighlySimilar() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TOC-001");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        List<String> headings = List.of(
                "1. 总体理解与建设目标",
                "1.1 项目背景分析",
                "1.2 总体建设目标",
                "2. 技术实施方案",
                "2.1 平台迁移路线图",
                "3. 项目管理与质量保证",
                "4. 投标报价汇总"
        );

        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            fields.add(headingField("FA" + i, "DOC-A", headings.get(i)));
            fields.add(headingField("FB" + i, "DOC-B", headings.get(i)));
        }
        data.setFields(fields);

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size());
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M4", hit.getRuleCode());
        assertTrue(hit.getTriggerSummary().contains("目录结构高度重合"));
        assertTrue(hit.getTriggerSummary().contains("7 处相同的章节标题"));
    }

    /**
     * 验证当只有少量标题重复时不会误报。
     */
    @Test
    void shouldNotHitWhenFewHeadingsMatch() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TOC-002");
        scope.setDocumentIds(List.of("DOC-A", "DOC-C"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                headingField("FA1", "DOC-A", "1. 总体理解"),
                headingField("FC1", "DOC-C", "1. 总体理解"),
                headingField("FA2", "DOC-A", "2. 技术方案"),
                headingField("FC2", "DOC-C", "2. 实施方案") // 不匹配
        ));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty());
    }

    private Field headingField(String fieldId, String documentId, String headingText) {
        Field field = new Field();
        field.setFieldId(fieldId);
        field.setDocumentId(documentId);
        field.setFieldType("heading");
        field.setNormalizedValue(headingText);
        field.setNormalizedKey("heading_key"); // 简化
        return field;
    }
}
