package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查决策报告数据。
 *
 * <p>封装报告的6个页面数据，提供给前端直接渲染使用。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportData {

    /** 第1页：审查结论总览 */
    private Page1Summary page1Summary;

    /** 第2页：风险总览 */
    private Page2RiskOverview page2RiskOverview;

    /** 第3页：核心证据 */
    private Page3CoreEvidence page3CoreEvidence;

    /** 第4页：详细比对 */
    private Page4DetailComparison page4DetailComparison;

    /** 第5页：处置建议 */
    private Page5ActionSuggestions page5ActionSuggestions;

    /** 第6页：附录 */
    private Page6Appendix page6Appendix;
}
