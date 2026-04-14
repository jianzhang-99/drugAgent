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
 * 评测任务实体。
 *
 * <p>记录一场完整的模型对比评测任务，包含baseline和challenger模型信息、
 * 任务状态、评测case数量等核心信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_task")
public class EvaluationTask {

    /**
     * 任务主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务状态：RUNNING / COMPLETED / FAILED
     */
    private String status;

    /**
     * Baseline模型名称
     */
    private String baselineModel;

    /**
     * Challenger模型名称
     */
    private String challengerModel;

    /**
     * 评测场景类型（如 TENDER_REVIEW）
     */
    private String sceneType;

    /**
     * 评测case总数
     */
    private Integer totalCases;

    /**
     * 已完成case数
     */
    private Integer completedCases;

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

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 失败原因（任务失败时记录）
     */
    private String failureReason;
}
