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
 * 评测偏差记录实体。
 *
 * <p>记录系统判定与人工判定之间的偏差，用于分析各规则的precision/recall/F1
 * 以及识别系统性偏差模式，为Prompt迭代提供依据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_deviation_record")
public class EvaluationDeviationRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的抽检样本ID
     */
    private String sampleId;

    /**
     * 关联的标书案例ID
     */
    private String caseId;

    /**
     * 规则编码（如 W-M1、W-P1 等）
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 偏差类型：FALSE_POSITIVE-假阳性（系统命中，人工未命中） /
     *           FALSE_NEGATIVE-假阴性（系统未命中，人工命中） /
     *           SCORE_DIFF-评分差异
     */
    private String deviationType;

    /**
     * 系统判定置信度（0.0~1.0）
     */
    private Double systemConfidence;

    /**
     * 人工判定置信度（0.0~1.0）
     */
    private Double manualConfidence;

    /**
     * 系统评分
     */
    private Integer systemScore;

    /**
     * 人工评分
     */
    private Integer manualScore;

    /**
     * 系统判定结果（JSON格式）
     */
    private String systemJudgment;

    /**
     * 人工判定结果（JSON格式）
     */
    private String manualJudgment;

    /**
     * 偏差说明
     */
    private String deviationDescription;

    /**
     * 建议的Prompt改进方向
     */
    private String promptImprovement;

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
