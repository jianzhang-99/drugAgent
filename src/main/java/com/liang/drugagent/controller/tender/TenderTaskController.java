package com.liang.drugagent.controller.tender;

import com.liang.drugagent.application.tender.TenderApplicationService;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 标书审查任务控制器。
 *
 * <p>负责任务生命周期管理。</p>
 *
 * @author liangjiajian
 */
@RestController
@RequestMapping("/api/tender/tasks")
@RequiredArgsConstructor
@Tag(name = "标书任务", description = "标书审查任务生命周期管理")
@CrossOrigin(origins = "*")
public class TenderTaskController {

    private final TenderApplicationService tenderApplicationService;

    @Operation(summary = "查询标书审查任务列表")
    @GetMapping
    public ResponseEntity<?> listTasks() {
        return ResponseEntity.ok(tenderApplicationService.listCases());
    }

    @Operation(summary = "执行审查任务")
    @PostMapping("/{caseId}/execute")
    public ResponseEntity<?> executeReview(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId) {
        try {
            TenderCase result = tenderApplicationService.executeReview(caseId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "审查执行失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "查询审查结果")
    @GetMapping("/{caseId}/result")
    public ResponseEntity<?> getReviewResult(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId) {
        Optional<TenderCase> resultOpt = tenderApplicationService.getReviewResult(caseId);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }
        return ResponseEntity.ok(resultOpt.get());
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{caseId}")
    public ResponseEntity<?> getTask(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId) {
        Optional<TenderCase> resultOpt = tenderApplicationService.getReviewResult(caseId);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }
        return ResponseEntity.ok(resultOpt.get());
    }
}
