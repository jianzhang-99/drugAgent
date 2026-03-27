package com.liang.drugagent.scene.tender_review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.drugagent.shared.model.RiskLevelEnum;
import com.liang.drugagent.shared.model.TaskStatusEnum;
import com.liang.drugagent.controller.domain.request.task_board.TaskCardQueryReq;
import com.liang.drugagent.controller.domain.response.task_board.TaskCardVO;
import com.liang.drugagent.controller.domain.response.task_board.TaskStatisticsVO;
import com.liang.drugagent.agent.common.entity.TaskCard;
import com.liang.drugagent.agent.common.mapper.TaskCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务卡片服务。
 *
 * <p>提供任务卡片的完整 CRUD 操作，支持：
 * <ul>
 *   <li>任务卡片创建（自动设置默认状态、优先级等）</li>
 *   <li>分页查询（支持多维度筛选）</li>
 *   <li>任务状态更新</li>
 *   <li>统计报表（各状态/风险等级的任务数量）</li>
 * </ul>
 *
 * @author drug-agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCardService {

    private final TaskCardMapper taskCardMapper;

    /**
     * 创建任务卡片。
     *
     * <p>如果字段为空则自动设置默认值：
     * <ul>
     *   <li>ID：自动生成 UUID</li>
     *   <li>创建时间：当前时间</li>
     *   <li>状态：PENDING</li>
     *   <li>风险等级：UNKNOWN</li>
     *   <li>优先级：5</li>
     *   <li>进度：0</li>
     * </ul>
     *
     * @param taskCard 待创建的任务卡片
     * @return 创建后的任务卡片（包含数据库生成的ID）
     */
    public TaskCard createTaskCard(TaskCard taskCard) {
        if (!StringUtils.hasText(taskCard.getId())) {
            taskCard.setId(UUID.randomUUID().toString());
        }
        if (taskCard.getCreatedAt() == null) {
            taskCard.setCreatedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(taskCard.getStatus())) {
            taskCard.setStatus(TaskStatusEnum.PENDING.getCode());
        }
        if (!StringUtils.hasText(taskCard.getRiskLevel())) {
            taskCard.setRiskLevel(RiskLevelEnum.UNKNOWN.getCode());
        }
        if (taskCard.getPriority() == null) {
            taskCard.setPriority(5);
        }
        if (taskCard.getProgress() == null) {
            taskCard.setProgress(0);
        }
        taskCardMapper.insert(taskCard);
        return taskCard;
    }

    /**
     * 分页查询任务卡片
     */
    public IPage<TaskCardVO> queryTaskCards(TaskCardQueryReq req) {
        LambdaQueryWrapper<TaskCard> wrapper = new LambdaQueryWrapper<>();

        // 状态筛选
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq(TaskCard::getStatus, req.getStatus());
        }
        // 任务类型筛选
        if (StringUtils.hasText(req.getTaskType())) {
            wrapper.eq(TaskCard::getTaskType, req.getTaskType());
        }
        // 场景筛选
        if (StringUtils.hasText(req.getScene())) {
            wrapper.eq(TaskCard::getScene, req.getScene());
        }
        // 风险等级筛选
        if (StringUtils.hasText(req.getRiskLevel())) {
            wrapper.eq(TaskCard::getRiskLevel, req.getRiskLevel());
        }
        // 提交人筛选
        if (StringUtils.hasText(req.getSubmittedBy())) {
            wrapper.eq(TaskCard::getSubmittedBy, req.getSubmittedBy());
        }
        // 指派人筛选
        if (StringUtils.hasText(req.getAssignedTo())) {
            wrapper.eq(TaskCard::getAssignedTo, req.getAssignedTo());
        }
        // 优先级筛选
        if (req.getPriority() != null) {
            wrapper.eq(TaskCard::getPriority, req.getPriority());
        }
        // 高风险筛选
        if (Boolean.TRUE.equals(req.getHighRiskOnly())) {
            wrapper.eq(TaskCard::getRiskLevel, RiskLevelEnum.HIGH.getCode());
            wrapper.ne(TaskCard::getStatus, TaskStatusEnum.COMPLETED.getCode());
        }
        // 未处理筛选
        if (Boolean.TRUE.equals(req.getUnhandledOnly())) {
            wrapper.in(TaskCard::getStatus,
                    TaskStatusEnum.PENDING.getCode(),
                    TaskStatusEnum.PARSING.getCode(),
                    TaskStatusEnum.PARSED.getCode(),
                    TaskStatusEnum.RUNNING.getCode());
        }
        // 即将到期筛选（3天内）
        if (Boolean.TRUE.equals(req.getDeadlineSoon())) {
            wrapper.le(TaskCard::getDeadline, LocalDateTime.now().plusDays(3));
            wrapper.ge(TaskCard::getDeadline, LocalDateTime.now());
            wrapper.notIn(TaskCard::getStatus,
                    TaskStatusEnum.COMPLETED.getCode(),
                    TaskStatusEnum.FAILED.getCode(),
                    TaskStatusEnum.CANCELLED.getCode());
        }
        // 关键词搜索
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(TaskCard::getTaskName, req.getKeyword())
                    .or()
                    .like(TaskCard::getSummary, req.getKeyword()));
        }

        // 排序
        if ("priority".equals(req.getSortField())) {
            wrapper.orderBy(true, "asc".equalsIgnoreCase(req.getSortOrder()),
                    TaskCard::getPriority, TaskCard::getCreatedAt);
        } else if ("riskLevel".equals(req.getSortField())) {
            wrapper.orderBy(true, "asc".equalsIgnoreCase(req.getSortOrder()),
                    TaskCard::getRiskLevel, TaskCard::getCreatedAt);
        } else if ("score".equals(req.getSortField())) {
            wrapper.orderBy(true, "desc".equalsIgnoreCase(req.getSortOrder()),
                    TaskCard::getScore);
        } else if ("updatedAt".equals(req.getSortField())) {
            wrapper.orderBy(true, "desc".equalsIgnoreCase(req.getSortOrder()),
                    TaskCard::getUpdatedAt);
        } else {
            wrapper.orderByDesc(TaskCard::getCreatedAt);
        }

        wrapper.eq(TaskCard::getIsDeleted, 0);

        Page<TaskCard> page = new Page<>(req.getPage(), req.getPageSize());
        IPage<TaskCard> pageResult = taskCardMapper.selectPage(page, wrapper);

        return pageResult.convert(this::convertToVO);
    }

    /**
     * 获取任务统计信息
     */
    public TaskStatisticsVO getTaskStatistics() {
        LambdaQueryWrapper<TaskCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskCard::getIsDeleted, 0);
        List<TaskCard> allTasks = taskCardMapper.selectList(wrapper);

        TaskStatisticsVO.TaskStatisticsVOBuilder builder = TaskStatisticsVO.builder();

        // 总量统计
        builder.totalCount(allTasks.size());
        builder.todayNewCount(taskCardMapper.countTodayNew());
        builder.todayCompletedCount(taskCardMapper.countTodayCompleted());

        // 状态分布
        Map<String, Long> statusCount = allTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus() == null ? "UNKNOWN" : t.getStatus(), Collectors.counting()));

        builder.pendingCount(statusCount.getOrDefault(TaskStatusEnum.PENDING.getCode(), 0L).intValue());
        builder.parsingCount(statusCount.getOrDefault(TaskStatusEnum.PARSING.getCode(), 0L).intValue() +
                statusCount.getOrDefault(TaskStatusEnum.PARSED.getCode(), 0L).intValue());
        builder.runningCount(statusCount.getOrDefault(TaskStatusEnum.RUNNING.getCode(), 0L).intValue());
        builder.completedCount(statusCount.getOrDefault(TaskStatusEnum.COMPLETED.getCode(), 0L).intValue());
        builder.failedCount(statusCount.getOrDefault(TaskStatusEnum.FAILED.getCode(), 0L).intValue());
        builder.statusDistribution(statusCount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue())));

        // 风险分布（只统计已完成的任务）
        List<TaskCard> completedTasks = allTasks.stream()
                .filter(t -> TaskStatusEnum.COMPLETED.getCode().equals(t.getStatus()))
                .collect(Collectors.toList());

        Map<String, Long> riskCount = completedTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getRiskLevel() == null ? "UNKNOWN" : t.getRiskLevel(), Collectors.counting()));

        builder.highRiskCount(riskCount.getOrDefault(RiskLevelEnum.HIGH.getCode(), 0L).intValue());
        builder.mediumRiskCount(riskCount.getOrDefault(RiskLevelEnum.MEDIUM.getCode(), 0L).intValue());
        builder.lowRiskCount(riskCount.getOrDefault(RiskLevelEnum.LOW.getCode(), 0L).intValue());
        builder.unknownRiskCount(riskCount.getOrDefault(RiskLevelEnum.UNKNOWN.getCode(), 0L).intValue());
        builder.riskDistribution(riskCount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue())));

        // 效率指标
        if (!completedTasks.isEmpty()) {
            Double avgScore = completedTasks.stream()
                    .filter(t -> t.getScore() != null)
                    .mapToInt(TaskCard::getScore)
                    .average()
                    .orElse(0.0);
            builder.avgScore(avgScore);

            // 计算完成率
            double completionRate = (double) completedTasks.size() / allTasks.size() * 100;
            builder.completionRate(Math.round(completionRate * 100.0) / 100.0);
        } else {
            builder.avgScore(0.0);
            builder.completionRate(0.0);
        }

        // Top列表
        List<TaskCard> topHighRisk = taskCardMapper.findTopHighRiskTasks();
        builder.topHighRiskTasks(topHighRisk.stream().map(this::convertToVO).collect(Collectors.toList()));

        List<TaskCard> upcomingDeadline = taskCardMapper.findUpcomingDeadlineTasks();
        builder.upcomingDeadlineTasks(upcomingDeadline.stream().map(this::convertToVO).collect(Collectors.toList()));

        return builder.build();
    }

    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(String taskId, String status) {
        TaskCard task = new TaskCard();
        task.setId(taskId);
        task.setStatus(status);

        if (TaskStatusEnum.RUNNING.getCode().equals(status)) {
            task.setStartedAt(LocalDateTime.now());
        } else if (TaskStatusEnum.COMPLETED.getCode().equals(status) ||
                TaskStatusEnum.FAILED.getCode().equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }

        return taskCardMapper.updateById(task) > 0;
    }

    /**
     * 更新任务进度
     */
    public boolean updateTaskProgress(String taskId, Integer progress, String currentStep) {
        TaskCard task = new TaskCard();
        task.setId(taskId);
        task.setProgress(progress);
        if (StringUtils.hasText(currentStep)) {
            task.setCurrentStep(currentStep);
        }
        return taskCardMapper.updateById(task) > 0;
    }

    /**
     * 获取单个任务卡片详情
     */
    public TaskCardVO getTaskCardById(String taskId) {
        TaskCard task = taskCardMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            return null;
        }
        return convertToVO(task);
    }

    /**
     * 获取所有任务卡片（不分页）
     */
    public List<TaskCard> getAllTaskCards() {
        LambdaQueryWrapper<TaskCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskCard::getIsDeleted, 0)
                .orderByDesc(TaskCard::getUpdatedAt);
        return taskCardMapper.selectList(wrapper);
    }

    /**
     * 根据traceId获取任务卡片
     */
    public TaskCard getTaskCardByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<TaskCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskCard::getTraceId, traceId)
                .eq(TaskCard::getIsDeleted, 0);
        List<TaskCard> tasks = taskCardMapper.selectList(wrapper);
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    /**
     * 实体转VO
     */
    private TaskCardVO convertToVO(TaskCard task) {
        TaskStatusEnum statusEnum = TaskStatusEnum.fromCode(task.getStatus());
        RiskLevelEnum riskLevelEnum = RiskLevelEnum.fromCode(task.getRiskLevel());

        TaskCardVO.TaskCardVOBuilder builder = TaskCardVO.builder()
                .id(task.getId())
                .caseId(task.getCaseId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .taskTypeText(getTaskTypeText(task.getTaskType()))
                .scene(task.getScene())
                .status(task.getStatus())
                .statusText(statusEnum.getDescription())
                .statusEnum(statusEnum)
                .progress(task.getProgress() == null ? 0 : task.getProgress())
                .currentStep(task.getCurrentStep())
                .riskLevel(task.getRiskLevel())
                .riskLevelText(riskLevelEnum.getDescription())
                .riskLevelEnum(riskLevelEnum)
                .submittedBy(task.getSubmittedBy())
                .assignedTo(task.getAssignedTo())
                .priority(task.getPriority())
                .priorityText(getPriorityText(task.getPriority()))
                .deadline(task.getDeadline())
                .score(task.getScore())
                .scoreGrade(getScoreGrade(task.getScore()))
                .summary(task.getSummary())
                .hitRules(task.getHitRules() == null ? 0 : task.getHitRules())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt());

        // 计算距截止时间
        if (task.getDeadline() != null) {
            long daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), task.getDeadline());
            builder.daysUntilDeadline(daysUntil);
            builder.isOverdue(daysUntil < 0);
        }

        // 计算执行耗时
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            long duration = ChronoUnit.MILLIS.between(task.getStartedAt(), task.getCompletedAt());
            builder.executionDuration(duration);
        }

        // 计算相对创建时间
        if (task.getCreatedAt() != null) {
            builder.createdAtAgo(getRelativeTime(task.getCreatedAt()));
        }

        // 判断是否需要关注
        boolean needsAttention = riskLevelEnum == RiskLevelEnum.HIGH ||
                (riskLevelEnum != RiskLevelEnum.UNKNOWN && statusEnum != TaskStatusEnum.COMPLETED);
        builder.needsAttention(needsAttention);

        // 可用操作
        builder.availableActions(getAvailableActions(statusEnum));

        return builder.build();
    }

    private String getTaskTypeText(String taskType) {
        if (taskType == null) return "未知";
        return switch (taskType) {
            case "TENDER_REVIEW" -> "标书审查";
            case "CONTRACT_CHECK" -> "合同检查";
            case "COMPLIANCE_ALERT" -> "合规预警";
            default -> taskType;
        };
    }

    private String getPriorityText(Integer priority) {
        if (priority == null) return "普通";
        return switch (priority) {
            case 1, 2 -> "紧急";
            case 3, 4 -> "高";
            case 5, 6 -> "普通";
            default -> "低";
        };
    }

    private String getScoreGrade(Integer score) {
        if (score == null) return "待评分";
        if (score >= 90) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 60) return "及格";
        return "不合格";
    }

    private String getRelativeTime(LocalDateTime dateTime) {
        long minutes = ChronoUnit.MINUTES.between(dateTime, LocalDateTime.now());
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        if (days < 30) return days + "天前";
        long months = days / 30;
        return months + "月前";
    }

    private List<String> getAvailableActions(TaskStatusEnum status) {
        List<String> actions = new ArrayList<>();
        switch (status) {
            case PENDING -> {
                actions.add("START");
                actions.add("CANCEL");
            }
            case PARSING, PARSED, RUNNING -> {
                actions.add("VIEW");
                actions.add("CANCEL");
            }
            case COMPLETED -> {
                actions.add("VIEW");
                actions.add("EXPORT");
                actions.add("ARCHIVE");
            }
            case FAILED -> {
                actions.add("RETRY");
                actions.add("VIEW");
            }
            case CANCELLED -> {
                actions.add("VIEW");
                actions.add("DELETE");
            }
        }
        return actions;
    }
}
