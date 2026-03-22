package com.liang.drugagent.controller;

import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.service.tenderreview.TenderCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务中心控制器
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/api/task-center")
@RequiredArgsConstructor
@Tag(name = "任务中心", description = "任务管理和查询接口")
public class TaskCenterController {

    private final TenderCaseService caseService;
    private final Map<String, List<SseEmitter>> taskEmitters = new ConcurrentHashMap<>();

    /**
     * 获取任务列表（分页）
     */
    @Operation(summary = "获取任务列表", description = "分页获取标书审查任务列表，支持状态和场景过滤")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功获取任务列表",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskPageResponse.class)))
    })
    @GetMapping("/tasks")
    public ResponseEntity<TaskPageResponse> getTaskList(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "任务状态") @RequestParam(required = false) String status,
            @Parameter(description = "场景标识") @RequestParam(required = false) String scene) {
        log.info("Getting task list: page={}, pageSize={}, status={}, scene={}", page, pageSize, status, scene);

        List<TenderCase> allTasks = caseService.listCases();

        // 过滤
        List<TenderCase> filteredTasks = allTasks.stream()
                .filter(task -> status == null || status.isEmpty() || status.equals(task.getStatus()))
                .filter(task -> scene == null || scene.isEmpty() || scene.equals(task.getScene()))
                .collect(Collectors.toList());

        // 分页
        int total = filteredTasks.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<TenderCase> pageTasks = fromIndex < total
                ? filteredTasks.subList(fromIndex, toIndex)
                : new ArrayList<>();

        TaskPageResponse response = new TaskPageResponse();
        response.setTasks(pageTasks);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages((total + pageSize - 1) / pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * 创建新任务
     */
    @Operation(summary = "创建新任务", description = "上传标书文件创建新的审查任务")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "任务创建成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenderCase.class))),
            @ApiResponse(responseCode = "400", description = "文件数量不足或格式不支持")
    })
    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createTask(
            @Parameter(description = "标书文件，至少2份", required = true) @RequestParam("files") MultipartFile[] files,
            @Parameter(description = "提交人") @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy) {
        log.info("Creating task: submittedBy={}, fileCount={}", submittedBy, files.length);

        List<String> filenames = new ArrayList<>();
        for (MultipartFile f : files) {
            filenames.add(f.getOriginalFilename());
        }

        com.liang.drugagent.domain.req.TenderCaseCreateReq req =
                com.liang.drugagent.domain.req.TenderCaseCreateReq.builder()
                        .filenames(filenames)
                        .submittedBy(submittedBy)
                        .build();

        com.liang.drugagent.domain.resp.TenderCaseCreateResp response;
        try {
            response = caseService.createCase(req);
        } catch (IllegalArgumentException e) {
            log.warn("Create task rejected: reason={}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        // 存储文件内容
        List<String> documentIds = response.getDocumentIds();
        for (int i = 0; i < files.length; i++) {
            try {
                caseService.storeFileContent(documentIds.get(i), files[i].getBytes());
            } catch (IOException e) {
                log.error("Store file failed: docId={}", documentIds.get(i), e);
                return ResponseEntity.status(500)
                        .body(Map.of("error", "文件读取失败: " + files[i].getOriginalFilename()));
            }
        }

        // 获取创建的任务
        Optional<TenderCase> taskOpt = caseService.getReviewResult(response.getCaseId());

        log.info("Task created: caseId={}, documentIds={}", response.getCaseId(), response.getDocumentIds());
        return ResponseEntity.status(201).body(taskOpt.orElse(null));
    }

    /**
     * 获取任务详情
     */
    @Operation(summary = "获取任务详情", description = "获取指定任务的详细信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功获取任务详情",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenderCase.class))),
            @ApiResponse(responseCode = "404", description = "任务不存在",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"error\":\"未找到任务: xxx\"}")))
    })
    @GetMapping("/tasks/{caseId}")
    public ResponseEntity<?> getTaskDetail(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId) {
        log.info("Getting task detail for caseId: {}", caseId);

        Optional<TenderCase> taskOpt = caseService.getReviewResult(caseId);
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(taskOpt.get());
        } else {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }
    }

    /**
     * 更新任务状态
     */
    @Operation(summary = "更新任务状态", description = "更新指定任务的状态")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "状态更新成功"),
            @ApiResponse(responseCode = "404", description = "任务不存在")
    })
    @PatchMapping("/tasks/{caseId}/status")
    public ResponseEntity<?> updateTaskStatus(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId,
            @RequestBody StatusUpdateRequest request) {
        log.info("Updating task status: caseId={}, status={}", caseId, request.getStatus());

        Optional<TenderCase> taskOpt = caseService.getReviewResult(caseId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }

        TenderCase task = taskOpt.get();
        String oldStatus = task.getStatus();
        task.setStatus(request.getStatus());

        // 保存更新后的任务
        caseService.saveCase(task);

        // 通知订阅者
        notifyTaskStatusChange(caseId, oldStatus, request.getStatus(), request.getMessage());

        return ResponseEntity.ok(task);
    }

    /**
     * 订阅任务进度
     */
    @Operation(summary = "订阅任务进度", description = "通过SSE订阅指定任务的实时进度更新")
    @GetMapping("/tasks/{caseId}/progress")
    public SseEmitter subscribeTaskProgress(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId) {
        log.info("New subscription for task progress: caseId={}", caseId);

        SseEmitter emitter = new SseEmitter(60_000L); // 60秒超时

        taskEmitters.computeIfAbsent(caseId, k -> new ArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(caseId, emitter));
        emitter.onTimeout(() -> removeEmitter(caseId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("caseId", caseId, "timestamp", Instant.now())));
        } catch (IOException e) {
            log.error("Failed to send connected event", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 推送任务进度
     */
    @Operation(summary = "推送任务进度", description = "向指定任务推送进度更新")
    @PostMapping("/tasks/{caseId}/progress")
    public ResponseEntity<?> pushTaskProgress(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId,
            @RequestBody ProgressUpdateRequest request) {
        log.info("Pushing task progress: caseId={}, step={}, progress={}", caseId, request.getStep(), request.getProgress());

        notifyTaskProgress(caseId, request.getStep(), request.getProgress(), request.getMessage());

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 获取任务统计信息
     */
    @Operation(summary = "获取任务统计", description = "获取任务统计信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功获取统计信息",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskStatistics.class)))
    })
    @GetMapping("/statistics")
    public ResponseEntity<TaskStatistics> getTaskStatistics() {
        log.info("Getting task statistics");

        List<TenderCase> allTasks = caseService.listCases();

        TaskStatistics stats = new TaskStatistics();
        stats.setTotalCount(allTasks.size());

        // 统计各状态任务数量
        long pendingCount = allTasks.stream()
                .filter(task -> "PENDING".equals(task.getStatus()) || "PARSED".equals(task.getStatus()))
                .count();
        long runningCount = allTasks.stream()
                .filter(task -> "RUNNING".equals(task.getStatus()))
                .count();
        long completedCount = allTasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()))
                .count();

        stats.setPendingCount((int) pendingCount);
        stats.setRunningCount((int) runningCount);
        stats.setCompletedCount((int) completedCount);

        // 统计风险等级分布
        long highRiskCount = allTasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()) && "HIGH".equals(task.getRiskLevel()))
                .count();
        long mediumRiskCount = allTasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()) && "MEDIUM".equals(task.getRiskLevel()))
                .count();
        long lowRiskCount = allTasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()) && "LOW".equals(task.getRiskLevel()))
                .count();

        stats.setHighRiskCount((int) highRiskCount);
        stats.setMediumRiskCount((int) mediumRiskCount);
        stats.setLowRiskCount((int) lowRiskCount);

        return ResponseEntity.ok(stats);
    }

    // ==================== 内部方法 ====================

    private void removeEmitter(String caseId, SseEmitter emitter) {
        List<SseEmitter> emitters = taskEmitters.get(caseId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                taskEmitters.remove(caseId);
            }
        }
    }

    private void notifyTaskStatusChange(String caseId, String oldStatus, String newStatus, String message) {
        List<SseEmitter> emitters = taskEmitters.get(caseId);
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            Map<String, Object> data = Map.of(
                    "caseId", caseId,
                    "oldStatus", oldStatus,
                    "newStatus", newStatus,
                    "message", message != null ? message : "",
                    "timestamp", Instant.now()
            );
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("statusChange").data(data));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    private void notifyTaskProgress(String caseId, String step, int progress, String message) {
        List<SseEmitter> emitters = taskEmitters.get(caseId);
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            Map<String, Object> data = Map.of(
                    "caseId", caseId,
                    "step", step != null ? step : "",
                    "progress", progress,
                    "message", message != null ? message : "",
                    "timestamp", Instant.now()
            );
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("progress").data(data));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    // ==================== 内部类 ====================

    @Data
    public static class TaskPageResponse {
        private List<TenderCase> tasks = new ArrayList<>();
        private int total;
        private int page;
        private int pageSize;
        private int totalPages;
    }

    @Data
    public static class StatusUpdateRequest {
        private String status;
        private String message;
    }

    @Data
    public static class ProgressUpdateRequest {
        private String step;
        private int progress;
        private String message;
    }

    @Data
    public static class TaskStatistics {
        private int totalCount;
        private int pendingCount;
        private int runningCount;
        private int completedCount;
        private int highRiskCount;
        private int mediumRiskCount;
        private int lowRiskCount;
    }
}