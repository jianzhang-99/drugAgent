package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心团队重叠规则执行器单元测试。
 *
 * @author liangjiajian
 */
class CoreTeamOverlapExecutorTest {

    private final CoreTeamOverlapExecutor executor = new CoreTeamOverlapExecutor();

    /**
     * 验证多名核心成员姓名和简历摘要一致时可以命中 W-M3。
     */
    @Test
    void shouldHitWhenCoreMembersAndResumesAreIdentical() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TEAM-001");
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                teamField("F-A-1", "DOC-A", "项目经理", "周辰", "主持过市域视频平台、雪亮工程及边界监控平台建设"),
                teamField("F-B-1", "DOC-B", "项目经理", "周辰", "主持过市域视频平台、雪亮工程及边界监控平台建设"),
                teamField("F-A-2", "DOC-A", "系统架构师", "李晗", "参与多地视频云平台整合与数据库迁移优化"),
                teamField("F-B-2", "DOC-B", "系统架构师", "李晗", "参与多地视频云平台整合与数据库迁移优化"),
                teamField("F-A-3", "DOC-A", "工程师", "陶睿", "负责前端大屏与运维流程配置"),
                teamField("F-B-3", "DOC-B", "工程师", "陶睿", "负责前端大屏与运维流程配置")
        ));

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size());
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M3", hit.getRuleCode());
        assertEquals("核心团队重叠", hit.getRuleName());
        assertTrue(hit.getTriggerSummary().contains("周辰"));
        assertTrue(hit.getTriggerSummary().contains("李晗"));
        assertEquals(6, hit.getEvidences().size());
    }

    /**
     * 验证只有姓名相同但简历不同不会误判命中。
     */
    @Test
    void shouldIgnoreWhenResumeContentDiffers() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TEAM-002");
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                teamField("F-A-1", "DOC-A", "项目经理", "周辰", "主持过市域视频平台项目"),
                teamField("F-B-1", "DOC-B", "项目经理", "周辰", "主持过智慧园区项目"),
                teamField("F-A-2", "DOC-A", "系统架构师", "李晗", "参与视频云平台整合"),
                teamField("F-B-2", "DOC-B", "系统架构师", "李晗", "负责数据库迁移实施")
        ));

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty());
        RuleHit hit = result.getHits().get(0);
        assertEquals(80, hit.getWeight());
        assertTrue(hit.getTriggerSummary().contains("周辰"));
    }

    /**
     * 构造测试用团队成员字段。
     *
     * @param fieldId 字段 ID
     * @param documentId 文档 ID
     * @param role 核心岗位
     * @param memberName 成员姓名
     * @param resumeSummary 简历摘要
     * @return 团队成员字段
     */
    private Field teamField(String fieldId, String documentId, String role, String memberName, String resumeSummary) {
        Field field = new Field();
        field.setFieldId(fieldId);
        field.setDocumentId(documentId);
        field.setBlockId("BLK-" + fieldId);
        field.setFieldType("team_member");
        field.setFieldName(role);
        field.setFieldValue(resumeSummary);
        field.setNormalizedValue(resumeSummary);
        field.setNormalizedKey(memberName);
        field.setChapterPath("第三章/核心团队配置");
        return field;
    }
}
