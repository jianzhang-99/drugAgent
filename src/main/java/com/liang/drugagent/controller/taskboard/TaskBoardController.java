package com.liang.drugagent.controller.taskboard;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.liang.drugagent.shared.domain.model.TaskStatusEnum;
import com.liang.drugagent.controller.domain.request.task_board.TaskCardQueryReq;
import com.liang.drugagent.controller.domain.request.task_board.TaskCardReq;
import com.liang.drugagent.controller.domain.response.task_board.TaskCardVO;
import com.liang.drugagent.controller.domain.response.task_board.TaskStatisticsVO;
import com.liang.drugagent.scene.common.entity.TaskCard;
import com.liang.drugagent.scene.tender_review.service.TaskCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务看板控制器
 * 提供任务看板相关的所有API接口
 *
 * @author drug-agent-team
 */
@Slf4j
@RestController
@RequestMapping("/api/task-board")
@RequiredArgsConstructor
@Tag(name = "任务看板", description = "任务看板管理和统计接口")
public class TaskBoardController {

    private final TaskCardService taskCardService;

    // ==================== 看板统计接口 ====================

    /**
     * 获取任务看板统计信息
     */
    @Operation(summary = "获取看板统计", description = "获取任务看板的统计数据，包括各状态数量、风险分布、效率指标等")
    @GetMapping("/statistics")
    public ResponseEntity<TaskStatisticsVO> getTaskStatistics() {
        log.info("Getting task board statistics");
        TaskStatisticsVO statistics = taskCardService.getTaskStatistics();
        return ResponseEntity.ok(statistics);
    }

    // ==================== 任务列表接口 ====================

    /**
     * 分页获取任务卡片列表
     */
    @Operation(summary = "获取任务列表", description = "分页获取任务卡片列表，支持多维度筛选和排序")
    @GetMapping("/tasks")
    public ResponseEntity<IPage<TaskCardVO>> getTaskList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "任务状态") @RequestParam(required = false) String status,
            @Parameter(description = "任务类型") @RequestParam(required = false) String taskType,
            @Parameter(description = "场景") @RequestParam(required = false) String scene,
            @Parameter(description = "风险等级") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortField,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "只看未处理的") @RequestParam(required = false) Boolean unhandledOnly,
            @Parameter(description = "只看高风险的") @RequestParam(required = false) Boolean highRiskOnly) {

        TaskCardQueryReq req = TaskCardQueryReq.builder()
                .page(page)
                .pageSize(pageSize)
                .status(status)
                .taskType(taskType)
                .scene(scene)
                .riskLevel(riskLevel)
                .keyword(keyword)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .unhandledOnly(unhandledOnly)
                .highRiskOnly(highRiskOnly)
                .build();

        log.info("Getting task list: page={}, pageSize={}, status={}, taskType={}", page, pageSize, status, taskType);
        IPage<TaskCardVO> result = taskCardService.queryTaskCards(req);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单个任务卡片详情
     */
    @Operation(summary = "获取任务详情", description = "获取指定任务卡片的详细信息")
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskDetail(
            @Parameter(description = "任务ID", required = true) @PathVariable String taskId) {
        log.info("Getting task detail: taskId={}", taskId);
        TaskCardVO taskCard = taskCardService.getTaskCardById(taskId);
        if (taskCard == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(taskCard);
    }

    // ==================== 任务操作接口 ====================

    /**
     * 创建新任务
     */
    @Operation(summary = "创建任务", description = "创建一个新的任务卡片")
    @PostMapping("/tasks")
    public ResponseEntity<TaskCardVO> createTask(@Valid @RequestBody TaskCardReq req) {
        log.info("Creating task: taskName={}, taskType={}", req.getTaskName(), req.getTaskType());

        TaskCard taskCard = TaskCard.builder()
                .taskName(req.getTaskName())
                .taskType(req.getTaskType())
                .scene(req.getScene())
                .submittedBy(req.getSubmittedBy())
                .assignedTo(req.getAssignedTo())
                .priority(req.getPriority())
                .deadline(req.getDeadline())
                .build();

        TaskCard created = taskCardService.createTaskCard(taskCard);
        TaskCardVO vo = taskCardService.getTaskCardById(created.getId());
        return ResponseEntity.ok(vo);
    }

    /**
     * 更新任务状态
     */
    @Operation(summary = "更新任务状态", description = "更新指定任务的状态")
    @PatchMapping("/tasks/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(
            @Parameter(description = "任务ID", required = true) @PathVariable String taskId,
            @Parameter(description = "新状态", required = true) @RequestBody Map<String, String> body) {
        String status = body.get("status");
        log.info("Updating task status: taskId={}, status={}", taskId, status);

        TaskStatusEnum statusEnum = TaskStatusEnum.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的状态值: " + status));
        }

        boolean success = taskCardService.updateTaskStatus(taskId, status);
        if (!success) {
            return ResponseEntity.notFound().build();
        }

        TaskCardVO taskCard = taskCardService.getTaskCardById(taskId);
        return ResponseEntity.ok(taskCard);
    }

    /**
     * 更新任务进度
     */
    @Operation(summary = "更新任务进度", description = "更新任务的执行进度")
    @PatchMapping("/tasks/{taskId}/progress")
    public ResponseEntity<?> updateTaskProgress(
            @Parameter(description = "任务ID", required = true) @PathVariable String taskId,
            @Parameter(description = "进度", required = true) @RequestBody Map<String, Object> body) {
        Integer progress = (Integer) body.get("progress");
        String currentStep = (String) body.get("currentStep");

        log.info("Updating task progress: taskId={}, progress={}, step={}", taskId, progress, currentStep);

        if (progress == null || progress < 0 || progress > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "进度值无效"));
        }

        boolean success = taskCardService.updateTaskProgress(taskId, progress, currentStep);
        if (!success) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("success", true, "progress", progress, "currentStep", currentStep));
    }

    // ==================== 筛选选项接口 ====================

    /**
     * 获取筛选项选项列表
     */
    @Operation(summary = "获取筛选项", description = "获取可用于筛选的选项列表")
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        log.info("Getting filter options");

        // 获取所有可选的状态
        Map<String, String> statusOptions = Map.of(
                TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.PENDING.getDescription(),
                TaskStatusEnum.PARSING.getCode(), TaskStatusEnum.PARSING.getDescription(),
                TaskStatusEnum.PARSED.getCode(), TaskStatusEnum.PARSED.getDescription(),
                TaskStatusEnum.RUNNING.getCode(), TaskStatusEnum.RUNNING.getDescription(),
                TaskStatusEnum.COMPLETED.getCode(), TaskStatusEnum.COMPLETED.getDescription(),
                TaskStatusEnum.FAILED.getCode(), TaskStatusEnum.FAILED.getDescription()
        );

        Map<String, String> taskTypeOptions = Map.of(
                "TENDER_REVIEW", "标书审查",
                "CONTRACT_CHECK", "合同检查",
                "COMPLIANCE_ALERT", "合规预警"
        );

        Map<String, String> riskLevelOptions = Map.of(
                "HIGH", "高风险",
                "MEDIUM", "中风险",
                "LOW", "低风险",
                "UNKNOWN", "待评估"
        );

        return ResponseEntity.ok(Map.of(
                "statusOptions", statusOptions,
                "taskTypeOptions", taskTypeOptions,
                "riskLevelOptions", riskLevelOptions
        ));
    }
}
