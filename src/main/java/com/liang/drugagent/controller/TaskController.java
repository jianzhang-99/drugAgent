package com.liang.drugagent.controller;

import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.shared.domain.model.TaskStatusEnum;
import com.liang.drugagent.scene.common.entity.TaskCard;
import com.liang.drugagent.scene.tender_review.service.TaskCardService;
import com.liang.drugagent.shared.domain.model.ReviewReport;
import com.liang.drugagent.shared.domain.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务与报告控制器。
 *
 * <p>提供任务查询和报告获取接口：
 * <ul>
 *   <li>GET /api/tasks/active - 获取活跃任务列表</li>
 *   <li>GET /api/reports/{traceId} - 根据traceId获取报告详情</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "任务与报告", description = "任务查询和报告获取接口")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskCardService taskCardService;
    private final TenderCaseService tenderCaseService;

    /**
     * 活跃任务数据VO。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ActiveTaskVO {
        private String id;
        private String name;
        private String scene;
        private String status;
        private Integer progress;
        private String updatedAt;
        private String traceId;
    }

    /**
     * 报告详情VO。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReportDetailVO {
        private String traceId;
        private String scene;
        private String riskLevel;
        private Integer score;
        private Integer docCount;
        private String summary;
        private List<String> managementSummary;
        private List<String> suggestedActions;
        private List<String> steps;
    }

    /**
     * 获取所有活跃任务。
     *
     * <p>返回状态为 PENDING、PARSING、PARSED、RUNNING 的任务。</p>
     */
    @Operation(summary = "获取活跃任务", description = "返回所有状态为执行中的任务列表")
    @GetMapping("/active")
    public Result<List<ActiveTaskVO>> getActiveTasks() {
        log.info("Getting active tasks");

        // 获取所有活跃状态
        List<String> activeStatuses = List.of(
                TaskStatusEnum.PENDING.getCode(),
                TaskStatusEnum.PARSING.getCode(),
                TaskStatusEnum.PARSED.getCode(),
                TaskStatusEnum.RUNNING.getCode()
        );

        // 查询所有任务并过滤活跃状态
        List<TaskCard> allTasks = taskCardService.getAllTaskCards();
        List<ActiveTaskVO> activeTasks = allTasks.stream()
                .filter(task -> activeStatuses.contains(task.getStatus()))
                .map(this::convertToActiveTaskVO)
                .collect(Collectors.toList());

        return Result.success(activeTasks);
    }

    /**
     * 根据traceId获取报告详情。
     *
     * <p>通过traceId查找对应的审查报告。</p>
     */
    @Operation(summary = "获取报告详情", description = "根据traceId获取审查报告详情")
    @GetMapping("/reports/{traceId}")
    public Result<ReportDetailVO> getReportByTraceId(
            @Parameter(description = "追踪ID", required = true) @PathVariable String traceId) {
        log.info("Getting report by traceId: {}", traceId);

        // 查找对应的TaskCard
        TaskCard taskCard = taskCardService.getTaskCardByTraceId(traceId);
        if (taskCard == null) {
            return Result.error("未找到对应的报告: " + traceId);
        }

        // 获取报告详情
        ReportDetailVO report = buildReportDetailVO(traceId, taskCard);
        return Result.success(report);
    }

    /**
     * 将TaskCard转换为ActiveTaskVO。
     */
    private ActiveTaskVO convertToActiveTaskVO(TaskCard task) {
        return ActiveTaskVO.builder()
                .id(task.getId())
                .name(task.getTaskName())
                .scene(task.getScene())
                .status(task.getStatus())
                .progress(task.getProgress())
                .updatedAt(task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : null)
                .traceId(task.getTraceId())
                .build();
    }

    /**
     * 构建报告详情VO。
     */
    private ReportDetailVO buildReportDetailVO(String traceId, TaskCard task) {
        // 获取文档数量
        int docCount = 0;
        if (task.getCaseId() != null) {
            Optional<TenderCase> tenderCase = tenderCaseService.getReviewResult(task.getCaseId());
            if (tenderCase.isPresent()) {
                docCount = tenderCase.get().getDocumentIds() != null
                        ? tenderCase.get().getDocumentIds().size() : 0;
            }
        }

        ReportDetailVO.ReportDetailVOBuilder builder = ReportDetailVO.builder()
                .traceId(traceId)
                .scene(task.getScene())
                .riskLevel(task.getRiskLevel())
                .score(task.getScore())
                .docCount(docCount)
                .summary(task.getSummary());

        // 从TaskCard的summary中解析管理建议和操作建议
        // 这里简化处理，实际可以从ReviewReport中获取
        if (task.getSummary() != null && !task.getSummary().isBlank()) {
            builder.managementSummary(List.of("建议人工复核高风险项目"));
            builder.suggestedActions(List.of("查看详细报告", "导出PDF"));
        } else {
            builder.managementSummary(List.of());
            builder.suggestedActions(List.of());
        }

        builder.steps(List.of("文档解析", "规则检查", "风险评估", "报告生成"));

        return builder.build();
    }
}
