package com.liang.drugagent.domain.workflow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标书审查报告对象。
 *
 * <p>用于承接 7.8 报告生成阶段的结构化输出，
 * 让页面展示、导出和追问解释复用同一份底层数据。</p>
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class ReviewReport {

    private String caseId;
    private String scene;
    private String generatedAt;
    private String markdownContent;
    private Overview overview;
    private List<RiskItem> riskItems = new ArrayList<>();
    private List<String> managementSummary = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private Map<String, String> explanations = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Overview {
        private Integer documentCount;
        private Integer rawHitCount;
        private Integer effectiveHitCount;
        private Integer exemptionCount;
        private Integer evidenceGroupCount;
        private Integer evidenceItemCount;
        private Integer score;
        private String riskLevel;
        private String summary;
    }

    @Getter
    @Setter
    public static class RiskItem {
        private String riskType;
        private String riskLevel;
        private String title;
        private String summary;
        private List<String> reasonCodes = new ArrayList<>();
        private List<String> evidenceTitles = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();
    }
}
