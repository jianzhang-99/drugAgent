package com.liang.drugagent.benchmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 成对评测结果实体。
 *
 * <p>记录同一case下两个模型的评测结果对比，包括评分、风险等级、命中规则等，
 * 并判定该case下的胜负关系。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_pair_result")
public class EvaluationPairResult {

    /**
     * 结果主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属评测任务ID
     */
    private Long taskId;

    /**
     * Case唯一标识
     */
    private String caseId;

    /**
     * Case描述（如"标书A vs 标书B"）
     */
    private String caseDescription;

    /**
     * Baseline模型评分
     */
    private Double baselineScore;

    /**
     * Challenger模型评分
     */
    private Double challengerScore;

    /**
     * Baseline风险等级：HIGH / MEDIUM / LOW
     */
    private String baselineRiskLevel;

    /**
     * Challenger风险等级：HIGH / MEDIUM / LOW
     */
    private String challengerRiskLevel;

    /**
     * Baseline命中规则编码（JSON数组格式）
     */
    private String baselineHitRules;

    /**
     * Challenger命中规则编码（JSON数组格式）
     */
    private String challengerHitRules;

    /**
     * 胜负判定：BASELINE / CHALLENGER / TIE
     */
    private String winner;

    /**
     * 判定理由
     */
    private String judgmentReason;

    /**
     * LLM评判置信度
     */
    private Double llmJudgeConfidence;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
