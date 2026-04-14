package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 详细比对（报告第4页）。
 *
 * <p>包含报价项对比表、团队成员对比表及文本高亮片段，
 * 用于支撑风险结论的详细举证。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page4DetailComparison {

    /** 报价对比 */
    private PriceComparison priceComparison;

    /** 团队对比 */
    private TeamComparison teamComparison;

    /** 文本高亮片段 */
    private List<TextHighlight> textHighlights;

    /**
     * 报价对比表。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceComparison {
        /** 表头 */
        private List<String> headers;
        /** 数据行 */
        private List<PriceRow> rows;
    }

    /**
     * 报价行。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRow {
        /** 报价项 */
        private String item;
        /** 文档A报价 */
        private String docA;
        /** 文档B报价 */
        private String docB;
        /** 差异 */
        private String diff;
        /** 判定结果：正常/异常 */
        private String verdict;
    }

    /**
     * 团队对比表。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamComparison {
        /** 表头 */
        private List<String> headers;
        /** 数据行 */
        private List<TeamRow> rows;
    }

    /**
     * 团队行。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamRow {
        /** 角色 */
        private String role;
        /** 文档A人员 */
        private String docA;
        /** 文档B人员 */
        private String docB;
        /** 判定结果：正常/重复 */
        private String verdict;
    }

    /**
     * 文本高亮片段。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextHighlight {
        /** 类别 */
        private String category;
        /** 文本A */
        private String textA;
        /** 文本B */
        private String textB;
        /** 相似度 */
        private String similarity;
        /** 判定结果 */
        private String verdict;
        /** 判定说明 */
        private String analysis;
    }
}
