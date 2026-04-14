package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 风险总览（报告第2页）。
 *
 * <p>按风险类型（报价/团队/文本相似/模板同源/辅助线索）分组展示各类风险概况，
 * 每类包含命中数量、是否需要人工复核、解释说明及代表性证据。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page2RiskOverview {

    /** 风险类别列表 */
    private List<RiskCategory> riskCategories;

    /**
     * 风险类别。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCategory {
        /** 类型：pricing/team/plagiarism/template/auxiliary */
        private String type;
        /** 类别名称：报价风险/团队风险/文本相似风险/模板同源风险/辅助线索 */
        private String categoryName;
        /** 等级：高/中/低 */
        private String level;
        /** 命中数量 */
        private Integer hitCount;
        /** 是否需要人工复核 */
        private Boolean needHumanReview;
        /** 解释说明 */
        private String explanation;
        /** 代表性证据 */
        private String representativeEvidence;
        /** 建议处置 */
        private String action;
    }
}
