package com.liang.drugagent.scene.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务卡片实体。
 *
 * <p>对应数据库表 {@code task_card}，用于管理和展示各类监管任务的工作卡片。</p>
 *
 * <p>任务类型 ({@code taskType}) 包括：
 * <ul>
 *   <li>TENDER_REVIEW - 标书审查任务</li>
 *   <li>CONTRACT_CHECK - 合同检查任务</li>
 *   <li>COMPLIANCE_ALERT - 合规预警任务</li>
 * </ul>
 *
 * <p>风险等级 ({@code riskLevel}) 枚举值：HIGH、MEDIUM、LOW、UNKNOWN</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_card")
public class TaskCard {

    /**
     * 任务卡片唯一标识(UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 关联的标书审查Case ID
     */
    private String caseId;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型: TENDER_REVIEW/CONTRACT_CHECK/COMPLIANCE_ALERT
     */
    private String taskType;

    /**
     * 场景标识
     */
    private String scene;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 进度百分比 0-100
     */
    private Integer progress;

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 风险等级: HIGH/MEDIUM/LOW/UNKNOWN
     */
    private String riskLevel;

    /**
     * 风险标签列表 (JSON)
     */
    private String riskTags;

    /**
     * 提交人
     */
    private String submittedBy;

    /**
     * 指派人
     */
    private String assignedTo;

    /**
     * 优先级 1-10 (1最高)
     */
    private Integer priority;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 综合评分 0-100
     */
    private Integer score;

    /**
     * 任务结果摘要
     */
    private String summary;

    /**
     * 命中规则数量
     */
    private Integer hitRules;

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
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 软删除标记: 0-未删除, 1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除原因
     */
    private String deleteReason;
}
