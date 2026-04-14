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
 * 人工抽检样本实体。
 *
 * <p>记录每次从历史case中抽出的待检样本，包含原始审查结果和人工审核结论，
 * 用于评估和改进标书审查系统的准确性。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manual_review_sample")
public class ManualReviewSample {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 样本唯一标识（UUID）
     */
    private String sampleId;

    /**
     * 关联的标书案例ID
     */
    private String caseId;

    /**
     * 抽样策略：RANDOM-随机抽样 / HIGH_DEVIATION-高偏差优先 / HIGH_RISK_BOUNDARY-高风险边界
     */
    private String selectionStrategy;

    /**
     * LLM置信度（0.0~1.0），仅LLM语义命中的规则有此字段
     */
    private Double llmConfidence;

    /**
     * 系统风险评分
     */
    private Integer riskScore;

    /**
     * 系统风险等级：HIGH / MEDIUM / LOW
     */
    private String riskLevel;

    /**
     * 审核状态：PENDING-待审核 / APPROVED-审核通过 / REJECTED-审核不通过
     */
    private String reviewStatus;

    /**
     * 审核人
     */
    private String reviewedBy;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 审核意见
     */
    private String reviewComment;

    /**
     * 系统判定结果（JSON格式，存储RuleHit列表）
     */
    private String systemResult;

    /**
     * 人工判定结果（JSON格式）
     */
    private String manualResult;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
