package com.liang.drugagent.tool.dto;

import com.liang.drugagent.shared.domain.model.EvidenceItem;
import com.liang.drugagent.shared.domain.model.ReviewReport;

import java.util.List;

/**
 * 标书审查工具返回对象。
 *
 * <p>作为 LLM 调用工具时的返回值，封装标书审查的完整结果。
 * 包含成功/失败状态、风险信息、证据列表和结构化报告。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不可变：使用 record 类型确保线程安全</li>
 *   <li>失败安全：提供静态工厂方法创建成功/失败结果</li>
 *   <li>可追溯：完整记录执行过程和结果</li>
 * </ul>
 *
 * @author liangjiajian
 */
public record ReviewTenderToolResult(
        /**
         * 是否成功。
         */
        boolean success,

        /**
         * 关联的案例ID。
         */
        String caseId,

        /**
         * 摘要（给LLM用）。
         */
        String summary,

        /**
         * 风险等级：high/medium/low。
         */
        String riskLevel,

        /**
         * 风险分。
         */
        Integer score,

        /**
         * 执行步骤。
         */
        List<String> steps,

        /**
         * 结构化报告。
         */
        ReviewReport report,

        /**
         * 证据列表。
         */
        List<EvidenceItem> evidenceList,

        /**
         * 错误/补充说明。
         */
        String message
) {
    /**
     * 创建成功结果。
     */
    public static ReviewTenderToolResult success(String caseId, String summary, String riskLevel,
                                                 Integer score, List<String> steps, ReviewReport report,
                                                 List<EvidenceItem> evidenceList) {
        return new ReviewTenderToolResult(true, caseId, summary, riskLevel, score, steps, report, evidenceList, null);
    }

    /**
     * 创建失败结果。
     */
    public static ReviewTenderToolResult failure(String message) {
        return new ReviewTenderToolResult(false, null, null, null, null, null, null, null, message);
    }
}
