package com.liang.drugagent.interfaces.http.response.task_board;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.liang.drugagent.core.domain.model.RiskLevelEnum;
import com.liang.drugagent.core.domain.model.TaskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务卡片视图对象
 * 用于任务看板API响应
 *
 * @author drug-agent-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCardVO {

    // ==================== 基础信息 ====================

    /**
     * 任务卡片唯一标识
     */
    private String id;

    /**
     * 关联的标书审查Case ID
     */
    private String caseId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务类型描述
     */
    private String taskTypeText;

    /**
     * 场景标识
     */
    private String scene;

    // ==================== 状态信息 ====================

    /**
     * 任务状态码
     */
    private String status;

    /**
     * 任务状态描述
     */
    private String statusText;

    /**
     * 任务状态枚举对象
     */
    private TaskStatusEnum statusEnum;

    /**
     * 进度百分比 0-100
     */
    private Integer progress;

    /**
     * 当前执行步骤
     */
    private String currentStep;

    // ==================== 风险信息 ====================

    /**
     * 风险等级码
     */
    private String riskLevel;

    /**
     * 风险等级描述
     */
    private String riskLevelText;

    /**
     * 风险等级枚举对象
     */
    private RiskLevelEnum riskLevelEnum;

    /**
     * 风险标签列表
     */
    private List<String> riskTags;

    /**
     * 风险项数量
     */
    private Integer riskItemCount;

    /**
     * 未处理风险项数量
     */
    private Integer unhandledRiskCount;

    /**
     * 是否需要关注（高风险或有待处理风险项）
     */
    private Boolean needsAttention;

    // ==================== 执行信息 ====================

    /**
     * 提交人
     */
    private String submittedBy;

    /**
     * 指派人
     */
    private String assignedTo;

    /**
     * 优先级 1-10
     */
    private Integer priority;

    /**
     * 优先级描述
     */
    private String priorityText;

    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime deadline;

    /**
     * 距截止时间剩余天数
     */
    private Long daysUntilDeadline;

    /**
     * 是否已过期
     */
    private Boolean isOverdue;

    // ==================== 结果信息 ====================

    /**
     * 综合评分 0-100
     */
    private Integer score;

    /**
     * 评分等级描述
     */
    private String scoreGrade;

    /**
     * 任务结果摘要
     */
    private String summary;

    /**
     * 命中规则数量
     */
    private Integer hitRules;

    // ==================== 阶段信息 ====================

    /**
     * 总阶段数
     */
    private Integer totalPhases;

    /**
     * 已完成阶段数
     */
    private Integer completedPhases;

    /**
     * 阶段详情列表
     */
    private List<PhaseVO> phases;

    // ==================== 时间信息 ====================

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 相对创建时间描述
     */
    private String createdAtAgo;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 开始执行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionDuration;

    // ==================== 操作信息 ====================

    /**
     * 可执行的操作列表
     */
    private List<String> availableActions;

    /**
     * 快捷操作按钮配置
     */
    private Map<String, ActionButton> actionButtons;

    // ==================== 内部类 ====================

    /**
     * 阶段视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseVO {
        private String id;
        private String phaseName;
        private Integer phaseOrder;
        private String status;
        private String statusText;
        private Integer progress;
        private String message;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime completedAt;
    }

    /**
     * 操作按钮配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionButton {
        private String key;
        private String label;
        private String icon;
        private String color;
        private String action;
        private Boolean disabled;
        private String confirmText;
    }
}
