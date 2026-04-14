package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 附录（报告第6页）。
 *
 * <p>包含规则列表、证据片段及任务信息，供后续审计和核查参考。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page6Appendix {

    /** 规则列表 */
    private List<RuleInfo> ruleList;

    /** 证据片段 */
    private List<EvidenceFragment> evidenceFragments;

    /** 任务信息 */
    private TaskInfo taskInfo;

    /**
     * 规则信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleInfo {
        /** 规则ID */
        private String ruleId;
        /** 规则编码 */
        private String ruleCode;
        /** 描述 */
        private String description;
    }

    /**
     * 证据片段。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceFragment {
        /** 片段ID */
        private String fragmentId;
        /** 内容 */
        private String content;
        /** 来源 */
        private String source;
    }

    /**
     * 任务信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {
        /** 任务ID */
        private String taskId;
        /** 审查时间 */
        private String reviewTime;
        /** 模型版本 */
        private String modelVersion;
    }
}
