package com.liang.drugagent.interfaces.http.response.task_board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 任务看板统计响应对象
 *
 * @author drug-agent-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatisticsVO {

    // ==================== 总量统计 ====================

    /**
     * 任务总数
     */
    private Integer totalCount;

    /**
     * 今日新增任务数
     */
    private Integer todayNewCount;

    /**
     * 今日完成任务数
     */
    private Integer todayCompletedCount;

    // ==================== 状态分布 ====================

    /**
     * 待处理任务数
     */
    private Integer pendingCount;

    /**
     * 解析中任务数
     */
    private Integer parsingCount;

    /**
     * 执行中任务数
     */
    private Integer runningCount;

    /**
     * 已完成任务数
     */
    private Integer completedCount;

    /**
     * 已失败任务数
     */
    private Integer failedCount;

    /**
     * 各状态数量映射
     */
    private Map<String, Integer> statusDistribution;

    // ==================== 风险分布 ====================

    /**
     * 高风险任务数
     */
    private Integer highRiskCount;

    /**
     * 中风险任务数
     */
    private Integer mediumRiskCount;

    /**
     * 低风险任务数
     */
    private Integer lowRiskCount;

    /**
     * 待评估任务数
     */
    private Integer unknownRiskCount;

    /**
     * 各风险等级数量映射
     */
    private Map<String, Integer> riskDistribution;

    // ==================== 效率指标 ====================

    /**
     * 平均完成时间（分钟）
     */
    private Double avgCompletionTime;

    /**
     * 平均评分
     */
    private Double avgScore;

    /**
     * 完成率
     */
    private Double completionRate;

    /**
     * 平均处理速度（任务/小时）
     */
    private Double avgProcessingSpeed;

    // ==================== 趋势数据 ====================

    /**
     * 近7天每日任务创建量
     */
    private Map<String, Integer> weeklyCreationTrend;

    /**
     * 近7天每日任务完成量
     */
    private Map<String, Integer> weeklyCompletionTrend;

    // ==================== Top列表 ====================

    /**
     * 高风险待处理任务列表（最多5条）
     */
    java.util.List<TaskCardVO> topHighRiskTasks;

    /**
     * 即将到期任务列表（最多5条）
     */
    java.util.List<TaskCardVO> upcomingDeadlineTasks;
}
