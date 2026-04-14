package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 核心证据（报告第3页）。
 *
 * <p>展示选取的核心证据列表，每条证据包含编号、类型、等级、可信度、
 * 标题、解释说明、关键发现、依据及建议处置。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page3CoreEvidence {

    /** 证据列表 */
    private List<Evidence> evidenceList;

    /**
     * 证据项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        /** 证据编号：E01, E02... */
        private String id;
        /** 类型 */
        private String type;
        /** 等级 */
        private String level;
        /** 可信度：高/中/低 */
        private String confidence;
        /** 标题 */
        private String title;
        /** 解释说明 */
        private String explanation;
        /** 关键发现 */
        private Map<String, Object> keyFindings;
        /** 依据 */
        private String basis;
        /** 建议处置 */
        private String action;
    }
}
