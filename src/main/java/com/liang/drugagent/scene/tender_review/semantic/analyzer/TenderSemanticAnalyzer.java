package com.liang.drugagent.scene.tender_review.semantic.analyzer;

import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;

import java.util.List;

/**
 * 语义分析器接口。
 * 用于对标书审查中的候选片段进行 LLM 语义判断。
 *
 * <p>每个实现类对应一个规则编码（如 W-P1、W-P4、W-M8），负责：
 * <ul>
 *   <li>召回同主题候选片段对</li>
 *   <li>调用语义裁决服务进行 LLM 判断</li>
 *   <li>将 LLM 判断结果转换为 RuleHit</li>
 * </ul>
 *
 * <p>接口输出保持与 {@link com.liang.drugagent.scene.tender_review.support.TenderRuleEngine} 一致的 {@link RuleHit} 结构，
 * 便于后续统一融合。
 *
 * @author architect
 */
public interface TenderSemanticAnalyzer {

    /**
     * 返回规则编码，如 "W-P1"、"W-P4"、"W-M8"。
     */
    String analyzerCode();

    /**
     * 对审查数据进行语义分析，返回命中的规则列表。
     *
     * @param data 标书审查结构化数据
     * @return 命中的规则列表（可能为空）
     */
    List<RuleHit> analyze(TenderReviewData data);
}
